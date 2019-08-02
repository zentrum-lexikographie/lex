(ns zdl-lex-server.solr
  (:require [clj-http.client :as http]
            [clojure.data.xml :as xml]
            [clojure.data.csv :as csv]
            [lucene-query.core :as lucene]
            [ring.util.http-response :as htstatus]
            [ring.util.io :as htio]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.article :as article]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def req
  (comp #(timbre/spy :trace %)
        #(dissoc % :http-client)
        http/request
        (partial merge (config :solr-req))
        #(timbre/spy :trace %)))

(def url (partial str (config :solr-base) "/" (config :solr-core)))

(defn build-suggestions [name]
  (req {:method :get :url (url "/suggest")
        :query-params {"suggest.dictionary" name "suggest.buildAll" "true"}
        :as :json }))

(defn suggest [name q]
  (req {:method :get :url (url "/suggest")
        :query-params {"suggest.dictionary" name "suggest.q" q}
        :as :json }))

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

(defn update-articles [action article->el articles]
  (batch-update (->> articles
                     (pmap article->el)
                     (keep identity)
                     (partition-all update-batch-size)
                     (map #(array-map :tag action
                                      :attrs {:commitWithin "10000"}
                                      :content %))
                     (map #(array-map :body (xml/emit-str %)
                                      :content-type :xml)))))

(def add-articles
  (partial update-articles
           :add
           #(try (article/document %) (catch Exception e (timbre/warn e (str %))))))

(def delete-articles
  (partial update-articles
           :delete
           (comp (partial array-map :tag :id :content)
                 store/file->id)))

(defn purge-articles [before-time]
  (update-articles :delete
                   #(array-map :tag :query :content %)
                   [(format "time_l:[* TO %s}" before-time)]))

(defn sync-articles []
  (let [sync-start (System/currentTimeMillis)
        articles (store/article-files)]
    (when-not (empty? (doall (add-articles articles)))
      (purge-articles sync-start)
      (commit-optimize))
    articles))

(defn handle-form-suggestions [{{:keys [q]} :params}]
  (let [solr-response (suggest "forms" (or q ""))
        path-prefix [:body :suggest :forms (keyword q)]
        total (get-in solr-response (conj path-prefix :numFound) 0)
        suggestions (get-in solr-response (conj path-prefix :suggestions) [])]
    (htstatus/ok
     {:total total
      :result (for [{:keys [term payload]} suggestions]
                (merge {:suggestion term} (read-string payload)))})))

(defn- facet-counts [[k v]] [k (:counts v)])

(defn- facet-values [[k v]] [(-> k name article/field-key)
                             (into (sorted-map) (->> v (partition 2) (map vec)))])


(defn- translate-field-names [node]
  (if (vector? node)
    (let [[type args] node]
      (condp = type
        :field (let [[_ name] args]
                 [:field [:term (-> name keyword article/field-name)]])
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

(def ^:private facet-params
  {"facet" "true"
   "facet.field" ["authors_ss"
                  "pos_ss"
                  "sources_ss"
                  "status_ss"
                  "tranche_ss"
                  "type_ss"]
   "facet.limit" "-1"
   "facet.mincount" "1"
   "facet.range" "timestamps_dts"
   "facet.range.start" "NOW/MONTH-1YEAR"
   "facet.range.end" "NOW"
   "facet.range.gap" "+1MONTH"})

(defn handle-search [{{:keys [q offset limit]
                :or {q "id:*" offset "0" limit "10"}} :params}]
  (let [params {"q" (translate-query q)}
        solr-response (query (merge query-params facet-params params))
        {:keys [response facet_counts]} (:body solr-response)
        {:keys [numFound docs]} response
        {:keys [facet_fields facet_ranges]} facet_counts
        facets (concat (map facet-values facet_fields)
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

(comment
  (->> (scroll {"q" "forms_ss:*"}) (take 55000) (last))
  (translate-query "forms:t*")
  (translate-query "text:*")
  (handle-search {:params {:q "suchen"}}))
