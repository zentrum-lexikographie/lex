(ns zdl-lex-server.solr
  (:require [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [lucene-query.core :as lucene]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [muuntaja.core :as m]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.bus :as bus]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-server.auth :as auth]
            [zdl-lex-server.csv :as csv]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.http-client :as http-client]
            [zdl-lex-server.solr.doc :refer [field-name->key field-key->name article->fields]]
            [zdl-lex-server.solr.query :as query]))

(def req
  (http-client/configure (env :solr-user) (env :solr-password)))

(def url
  (partial str (env :solr-base) (env :solr-core)))

(defn suggest [name q]
  (req {:method :get :url (url "/suggest")
        :query-params {"suggest.dictionary" name "suggest.q" q}
        :as :json }))

(defn query [params]
  (req {:method :get :url (url "/query")
        :query-params params
        :as :json}))

(defn scroll
  ([params] (scroll params 20000))
  ([params page-size] (scroll params page-size 0))
  ([params page-size page]
   (let [offset (* page page-size)
         resp (query (merge params {"start" offset "rows" page-size}))
         docs (get-in resp [:body :response :docs] [])]
     (concat
      docs
      (if-not (< (count docs) page-size)
        (lazy-seq (scroll params page-size (inc page))))))))

(def ^:private update-batch-size 2000)

(def ^:private update-req
  {:method :post
   :url (url "/update")
   :query-params {:wt "json"}
   :as :json})

(defn batch-update [updates]
  (doall (pmap (comp req (partial merge update-req)) updates)))

(def commit-optimize
  (partial batch-update [{:body "<update><commit/><optimize/></update>"
                          :content-type :xml}]))

(defn update-articles [articles->xml articles]
  (batch-update (->> articles
                     (partition-all update-batch-size)
                     (pmap articles->xml)
                     (map #(array-map :body (xml/serialize %)
                                      :content-type :xml)))))

(defn- articles->add-xml [article-files]
  (let [articles (article/articles git/articles-dir)
        doc (xml/new-document)
        el #(.createElement doc %)
        add (doto (el "add") (.setAttribute "commitWithin" "10000"))]
    (doseq [file article-files]
      (try
        (doseq [{:keys [id] :as article} (articles file)
                :let [article-doc (el "doc")]]
          (doseq [[n v] (article->fields article)]
            (doto article-doc
              (.appendChild
               (doto (el "field")
                 (.setAttribute "name" n)
                 (.setTextContent v)))))
            (doto add (.appendChild article-doc)))
        (catch Exception e (timbre/warn e file))))
    (doto doc (.appendChild add))))

(def add-articles (partial update-articles articles->add-xml))

(defn- articles->delete-xml [article-files]
  (let [file->id (article/file->id git/articles-dir)
        doc (xml/new-document)
        el #(.createElement doc %)
        del (doto (el "delete") (.setAttribute "commitWithin" "10000"))]
    (doseq [id (map file->id article-files)]
      (doto del
        (.appendChild
         (doto (el "id") (.setTextContent id)))))
    (doto doc (.appendChild del))))

(def delete-articles (partial update-articles articles->delete-xml))

(defn index-git-changes
  "Synchronizes modified articles with the Solr index"
  [changes]
  (let [article-xml-file? (article/article-xml-file? git/articles-dir)
        file->id (article/file->id git/articles-dir)
        articles (filter article-xml-file? changes)
        modified (filter fs/exists? articles)
        deleted (remove fs/exists? articles)]
    (doseq [m modified]
      (timbre/info {:solr {:modified (file->id m)}}))
    (doseq [d deleted]
      (timbre/info {:solr {:deleted (file->id d)}}))
    (try
      (add-articles modified)
      (delete-articles deleted)
      (catch Throwable t (timbre/warn t)))))

(defstate git-change-indexer
  :start (bus/listen :git-changes index-git-changes)
  :stop (git-change-indexer))

(defn- query->delete-xml [[query]]
  (let [doc (xml/new-document)
        el #(.createElement doc %)]
    (doto doc
      (.appendChild
       (doto (doto (el "delete") (.setAttribute "commitWithin" "10000"))
         (.appendChild
          (doto (el "query") (.setTextContent query))))))))

(defn rebuild-index []
  (let [sync-start (System/currentTimeMillis)
        articles (article/article-xml-files git/articles-dir)]
    (when-not (empty? (doall (add-articles articles)))
      (update-articles query->delete-xml [(format "time_l:[* TO %s}" sync-start)])
      (commit-optimize))
    articles))

(defstate index-rebuild-scheduler
  "Synchronizes all articles with the Solr index"
  :start (cron/schedule "0 1 0 * * ?" "Solr index rebuild" rebuild-index)
  :stop (a/close! index-rebuild-scheduler))

(defn index-empty? []
  (= 0 (get-in (query {"q" "id:*" "rows" "0"}) [:body :response :numFound] -1)))

(defstate index-init
  :start (when (index-empty?) (a/>!! index-rebuild-scheduler :init)))

(defn handle-index-rebuild [{:keys [auth/user]}]
  (if (= "admin" user)
    (htstatus/ok
     {:index (a/>!! index-rebuild-scheduler :sync)})
    (htstatus/forbidden
     {:index false})))

(defn build-suggestions [name]
  (req {:method :get :url (url "/suggest")
        :query-params {"suggest.dictionary" name "suggest.buildAll" "true"}
        :as :json}))

(def ^:private build-forms-suggestions
  (partial build-suggestions "forms"))

(defstate build-suggestions-scheduler
  "Synchronizes the forms suggestions with all indexed articles"
  :start (cron/schedule "0 */10 * * * ?" "Forms FSA update" build-forms-suggestions)
  :stop (a/close! build-suggestions-scheduler))

(defn id-exists? [id]
  (let [q [:query
           [:clause
            [:field [:term "id"]]
            [:value [:pattern (str "*" id "*")]]]]]
    (some->>
     (query {"q" (lucene/ast->str q) "rows" 0})
     :body :response :numFound
     (< 0))))

(defn- facet-counts [[k v]]
  [k (:counts v)])

(defn- facet-values [[k v]]
  [(-> k name field-name->key)
   (into (sorted-map) (->> v (partition 2) (map vec)))])

(defn- facet-intervals [[k v]]
  [(-> k name field-name->key)
   (into (sorted-map) (for [[k v] v] [(name k) v]))])

(defn docs->results [docs]
  (for [{:keys [abstract_ss]} docs]
    (-> abstract_ss first read-string)))

(def ^:private query-params
  {"df" "forms_ss"
   "sort" "forms_ss asc,weight_i desc,id asc"})

(defn- ->timestamp [dt]
  (-> dt (t/at (t/midnight)) (t/in "UTC")))

(def timestamp->str (partial t/format :iso-offset-date-time))

(defn- facet-params []
  (let [today (->timestamp (t/today))
        year (->timestamp (.. (t/year) (atDay 1)))
        tomorrow (->timestamp (t/tomorrow))
        boundaries (concat
                    [today
                     (t/- tomorrow (t/new-period 7 :days))
                     (t/- tomorrow (t/new-period 1 :months))]
                    (for [i (range 4)]
                      (let [year (t/- year (t/new-period i :years))] year)))
        tomorrow (timestamp->str tomorrow)
        boundaries (map timestamp->str boundaries)]
    {"facet" "true"
     "facet.field" ["authors_ss" "editors_ss"
                    "sources_ss" "tranche_ss"
                    "type_ss" "pos_ss" "status_ss"]
     "facet.limit" "-1"
     "facet.mincount" "1"
     "facet.interval" "timestamp_dt"
     "facet.interval.set" (for [b boundaries]
                            (format "{!key=\"%s\"}[%s,%s)" b b tomorrow))}))

(defn handle-form-suggestions [{{:keys [q]} :params}]
  (let [solr-response (suggest "forms" (or q ""))
        path-prefix [:body :suggest :forms (keyword q)]
        total (get-in solr-response (conj path-prefix :numFound) 0)
        suggestions (get-in solr-response (conj path-prefix :suggestions) [])]
    (htstatus/ok
     {:total total
      :result (for [{:keys [term payload]} suggestions]
                (merge {:suggestion term} (read-string payload)))})))

(defn handle-search [req]
  (let [params (-> req :parameters :query)
        {:keys [q offset limit] :or {q "id:*" offset 0 limit 1000}} params
        params {"q" (query/translate q) "start" offset "rows" limit}
        solr-response (query (merge query-params (facet-params) params))
        {:keys [response facet_counts]} (:body solr-response)
        {:keys [numFound docs]} response
        {:keys [facet_fields facet_ranges facet_intervals]} facet_counts
        facets (concat (map facet-values facet_fields)
                       (map facet-intervals facet_intervals)
                       (map (comp facet-values facet-counts) facet_ranges))]
    (htstatus/ok
     {:total numFound
      :result (docs->results docs)
      :facets (into (sorted-map) facets)})))

(defn- doc->csv [d]
  [(d :status)
   (d :source)
   (some->> d :forms (str/join ", "))
   (some->> d :definitions first)
   (d :type)
   (d :timestamp)
   (d :author)
   (d :editor)
   (d :id)])

(def csv-header ["Status" "Quelle" "Schreibung" "Definition" "Typ"
                 "Datum" "Autor" "Redakteur" "ID"])
(defn handle-export [req]
  (let [params (-> req :parameters :query)
        {:keys [q limit] :or {q "id:*" limit 1000}} params
        params (merge query-params {"q" (query/translate q)})
        docs (->> (scroll params (min limit 50000)) (take limit))
        records (->> docs docs->results (map doc->csv) (cons csv-header))]
    (->
     (htstatus/ok records)
     (htstatus/update-header "Content-Disposition" str
                             "attachment; filename=\"zdl-dwds-export-"
                             (t/format :iso-date-time (t/date-time))
                             ".csv\""))))

(s/def ::pos-int (s/and int? (some-fn pos? zero?)))
(s/def ::q string?)
(s/def ::offset ::pos-int)
(s/def ::limit ::pos-int)
(s/def ::search-query (s/keys :opt-un [::q ::offset ::limit]))
(s/def ::export-query (s/keys :opt-un [::q ::limit]))
(s/def ::suggestion-query (s/keys :req-un [::q]))

(def ring-handlers
  ["/index"
   [""
    {:get {:summary "Query the full-text index"
           :tags ["Index" "Query"]
           :parameters {:query ::search-query}
           :handler handle-search}
     :delete {:summary "Clears the index, forcing a rebuild"
              :tags ["Index", "Admin"]
              :handler handle-index-rebuild}}]

   ["/export"
    {:get {:summary "Export index metadata in CSV format"
           :tags ["Index" "Query" "Export"]
           :parameters {:query ::export-query}
           :muuntaja (m/create (assoc m/default-options
                                      :return :output-stream
                                      :default-format "text/csv"
                                      :formats {"text/csv" csv/format}))
           :handler handle-export}}]

   ["/forms/suggestions"
    {:get {:summary "Retrieve suggestion for headwords based on prefix queries"
           :tags ["Index" "Query" "Suggestions" "Headwords"]
           :parameters {:query ::suggestion-query}
           :handler handle-form-suggestions}}]])

(comment
  (->> (for [article (article/articles-in git/articles-dir)
             :when (and (not (= "WDG" (:source article))) (:references article))]
         (article->fields article))
       (take 10))
  (xml/serialize
   (query->delete-xml [(format "time_l:[* TO %s}" "now")]))
  (time
   (xml/serialize
    (articles->add-xml (take 2 (article/article-xml-files git/articles-dir)))))
  (time (last (rebuild-index)))
  (time (->> (scroll {"q" "forms_ss:*"}) (take 50000) (last)))
  (translate-query "forms:t*")
  (translate-query "text:*")
  (handle-search {:params {:q "id:*"}}))
