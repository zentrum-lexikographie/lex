(ns zdl-lex-server.solr
  (:require [clj-http.client :as http]
            [clojure.core.async :as async]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [lucene-query.core :as lucene]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [ring.util.io :as htio]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-server.cron :as cron]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.status :as status]
            [zdl-lex-server.store :as store]))

(defn- field-name->key
  "Translates a Solr field name into a keyword."
  [n]
  (condp = n
    "_text_" :text
    (-> n
        (str/replace #"_((dts)|(dt)|(s)|(ss)|(t)|(i)|(l))$" "")
        (str/replace "_" "-")
        keyword)))

(defn- field-name-suffix [k]
  (condp = k
    :id ""
    :language ""
    :xml-descendent-path ""
    :weight "_i"
    :time "_l"
    :definitions "_t"
    :last-modified "_dt"
    :timestamp "_dt"
    :timestamps "_dts"
    :author "_s"
    :source "_s"
    "_ss"))
(defn- field-key->name
  "Translates a keyword into a Solr field name."
  [k]
  (condp = k
    :text "_text_"
    (let [field-name (str/replace (name k) "-" "_")
          field-suffix (field-name-suffix k)]
      (str field-name field-suffix))))

(let [abstract-fields [:type :status
                       :last-modified :timestamp
                       :author :authors :sources :source
                       :forms :pos :definitions]
      basic-field (fn [[k v]]
                    (if-not (nil? v)
                      [(field-key->name k) (if (coll? v) (vec v) [(str v)])]))
      attr-field (fn [prefix suffix attrs]
                   (let [all-values (->> attrs vals (apply concat) (seq))]
                     (-> (for [[type values] attrs]
                           (let [type (-> type name str/lower-case)
                                 field (str prefix "_" type "_" suffix)]
                             [field values]))
                         (conj (if all-values
                                 [(str prefix "_" suffix) all-values])))))]
  (defn article->fields
    "Returns Solr fields/values for a given article ID and excerpt."
    [id excerpt]
    (let [abstract (merge {:id id} (select-keys excerpt abstract-fields))
          preamble {:id id
                    :language "de"
                    :time (str (System/currentTimeMillis))
                    :xml-descendent-path id
                    :abstract (pr-str abstract)}
          main-fields (dissoc excerpt :timestamps :authors :sources)
          fields (->> [(map basic-field preamble)
                       (map basic-field main-fields)
                       (attr-field "timestamps" "dts" (excerpt :timestamps))
                       (attr-field "authors" "ss" (excerpt :authors))
                       (attr-field "sources" "ss" (excerpt :sources))]
                      (mapcat identity)
                      (remove nil?)
                      (into {}))]
      (for [[name values] (sort fields) value (sort values)]
        [name value]))))

(def req
  (comp #(timbre/spy :trace %)
        #(dissoc % :http-client)
        http/request
        (partial merge (config :solr-req))
        #(timbre/spy :trace %)))

(def url (partial str (config :solr-base) "/" (config :solr-core)))

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
  (let [doc (xml/new-document)
        el #(.createElement doc %)
        add (doto (el "add") (.setAttribute "commitWithin" "10000"))]
    (doseq [f article-files :let [id (store/file->id f)]]
      (try
        (doseq [a (-> (xml/parse f) (article/doc->articles))
                :let [article-doc (el "doc")]]
          (doseq [[n v] (->> (article/excerpt a) (article->fields id))]
            (doto article-doc
              (.appendChild
               (doto (el "field")
                 (.setAttribute "name" n)
                 (.setTextContent v)))))
            (doto add (.appendChild article-doc)))
        (catch Exception e (timbre/warn e id))))
    (doto doc (.appendChild add))))

(def add-articles (partial update-articles articles->add-xml))

(defn- articles->delete-xml [article-files]
  (let [doc (xml/new-document)
        el #(.createElement doc %)
        del (doto (el "delete") (.setAttribute "commitWithin" "10000"))]
    (doseq [f article-files :let [id (store/file->id f)]]
      (doto del
        (.appendChild
         (doto (el "id") (.setTextContent id)))))
    (doto doc (.appendChild del))))

(def delete-articles (partial update-articles articles->delete-xml))

(defstate git-changes->solr
  "Synchronizes modified articles with the Solr index"
  :start (let [stop-ch (async/chan)]
           (async/go-loop []
             (when-let [changes (async/alt! git/changes ([v] v) stop-ch nil)]
               (let [articles (filter store/article-file? changes)
                     modified (filter fs/exists? articles)
                     deleted (remove fs/exists? articles)]
                 (doseq [m modified]
                   (timbre/info {:solr {:modified (store/file->id m)}}))
                 (doseq [d deleted]
                   (timbre/info {:solr {:deleted (store/file->id d)}}))
                 (async/<!
                  (async/thread
                    (try
                      (add-articles modified)
                      (delete-articles deleted)
                      (catch Throwable t (timbre/warn t))))))
               (recur)))
           stop-ch)
  :stop (async/close! git-changes->solr))

(defn- query->delete-xml [[query]]
  (let [doc (xml/new-document)
        el #(.createElement doc %)]
    (doto doc
      (.appendChild
       (doto (doto (el "delete") (.setAttribute "commitWithin" "10000"))
         (.appendChild
          (doto (el "query") (.setTextContent query))))))))

(defn purge-articles [before-time]
  (update-articles query->delete-xml [(format "time_l:[* TO %s}" before-time)]))

(defn sync-articles []
  (let [sync-start (System/currentTimeMillis)
        articles (store/article-files)]
    (when-not (empty? (doall (add-articles articles)))
      (purge-articles sync-start)
      (commit-optimize))
    articles))

(defstate git-all->solr
  "Synchronizes all articles with the Solr index"
  :start (cron/schedule "0 1 0 * * ?" "Solr index rebuild" sync-articles)
  :stop (async/close! git-all->solr))


(defn handle-index-trigger [req]
  (if (= "admin" (status/user req))
    (htstatus/ok
     {:index (async/>!! git-all->solr :sync)})
    (htstatus/forbidden
     {:index false})))

(defn build-suggestions [name]
  (req {:method :get :url (url "/suggest")
        :query-params {"suggest.dictionary" name "suggest.buildAll" "true"}
        :as :json }))

(def ^:private build-forms-suggestions
  (partial build-suggestions "forms"))

(defstate index->suggestions
  "Synchronizes the forms suggestions with all indexed articles"
  :start (cron/schedule "0 */10 * * * ?" "Forms FSA update" build-forms-suggestions)
  :stop (async/close! index->suggestions))

(defn suggest [name q]
  (req {:method :get :url (url "/suggest")
        :query-params {"suggest.dictionary" name "suggest.q" q}
        :as :json }))

(defn handle-form-suggestions [{{:keys [q]} :params}]
  (let [solr-response (suggest "forms" (or q ""))
        path-prefix [:body :suggest :forms (keyword q)]
        total (get-in solr-response (conj path-prefix :numFound) 0)
        suggestions (get-in solr-response (conj path-prefix :suggestions) [])]
    (htstatus/ok
     {:total total
      :result (for [{:keys [term payload]} suggestions]
                (merge {:suggestion term} (read-string payload)))})))

(defn query [params]
  (req {:method :get :url (url "/query")
        :query-params params
        :as :json}))

(defn scroll
  ([params] (scroll params 10000))
  ([params page-size] (scroll params page-size 0))
  ([params page-size page]
   (let [offset (* page page-size)
         resp (query (merge params {"start" offset "rows" page-size}))]
     (if-let [docs (seq (get-in resp [:body :response :docs] []))]
       (concat docs (lazy-seq (scroll params page-size (inc page))))))))

(defn- facet-counts [[k v]]
  [k (:counts v)])

(defn- facet-values [[k v]]
  [(-> k name field-name->key)
   (into (sorted-map) (->> v (partition 2) (map vec)))])

(defn- facet-intervals [[k v]]
  [(-> k name field-name->key)
   (into (sorted-map) (for [[k v] v] [(name k) v]))])

(defn- translate-field-names [node]
  (if (vector? node)
    (let [[type args] node]
      (condp = type
        :field (let [[_ name] args]
                 [:field [:term (-> name keyword field-key->name)]])
        (vec (map translate-field-names node))))
    node))

(defn- translate-query [s]
  (try
    (-> s lucene/str->ast translate-field-names lucene/ast->str)
    (catch Throwable t s)))

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
     "facet.field" ["authors_ss" "sources_ss" "tranche_ss"
                    "type_ss" "pos_ss" "status_ss"]
     "facet.limit" "-1"
     "facet.mincount" "1"
     "facet.interval" "timestamp_dt"
     "facet.interval.set" (for [b boundaries]
                            (format "{!key=\"%s\"}[%s,%s)" b b tomorrow))}))

(defn handle-search [{{:keys [q offset limit]
                :or {q "id:*" offset "0" limit "10"}} :params}]
  (let [params {"q" (translate-query q) "start" offset "rows" limit}
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

(defn handle-export [{{:keys [q limit] :or {q "id:*" limit "10"}} :params}]
  (let [params (merge query-params {"q" (translate-query q)})
        limit (Integer/parseInt limit)
        docs (->> (scroll params) (take limit))]
    (->
     (htio/piped-input-stream
      (fn [out]
        (let [w (io/writer out :encoding "UTF-8")]
          (csv/write-csv w [["Schreibung"
                             "Wortklasse"
                             "Artikeltyp"
                             "ID"]])
          (doseq [d (docs->results docs)]
            (csv/write-csv w [[(some->> d :forms (str/join "|"))
                               (some->> d :pos (str/join "|"))
                               (d :type)
                               (d :id)]]))
          (.flush w))))
     (htstatus/ok)
     (htstatus/update-header "Content-Type" str "text/csv"))))

(defn id-exists? [id]
  (let [q [:query
           [:clause
            [:field [:term "id"]]
            [:value [:pattern (str "*" id "*")]]]]]
    (some->>
     (query {"q" (lucene/ast->str q) "rows" 0})
     :body :response :numFound
     (< 0))))

(comment
  (for [f (take 10 (drop 10000 (store/article-files)))
        :let [id (store/file->id f)]
        ;;:when (= id "DWDS/002-Minimalartikel/Ausbaustufe.xml")
        a (-> (xml/parse f) (article/doc->articles))
        :let [ex (article/excerpt a)]]
    (do
      (article->fields id ex)))
  (xml/serialize
   (query->delete-xml [(format "time_l:[* TO %s}" "now")]))
  (time
   (xml/serialize
    (articles->add-xml (take 2 (store/article-files)))))
  (time (last (sync-articles)))
  (->> (scroll {"q" "forms_ss:*"}) (take 55000) (last))
  (translate-query "forms:t*")
  (translate-query "text:*")
  (handle-search {:params {:q "id:*"}}))
