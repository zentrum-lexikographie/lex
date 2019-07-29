(ns zdl-lex-server.solr
  (:require [clj-http.client :as http]
            [clojure.data.xml :as xml]
            [lucene-query.core :as lucene]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.article :as article]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store]))

(def req
  (comp #(timbre/spy :debug %)
        #(dissoc % :http-client)
        http/request
        (partial merge (config :solr-req))
        #(timbre/spy :debug %)))

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


(defn handle-search [{{:keys [q offset limit]
                :or {q "id:*" offset "0" limit "10"}} :params}]
  (let [q (translate-query q)
        solr-response (query {"q" q
                              "df" "forms_ss"
                              "start" offset
                              "rows" limit
                              "sort" "forms_ss asc,weight_i desc,id asc"
                              "facet" "true"
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
        {:keys [response facet_counts]} (:body solr-response)
        {:keys [numFound docs]} response
        {:keys [facet_fields facet_ranges]} facet_counts
        facets (concat (map facet-values facet_fields)
                       (map (comp facet-values facet-counts) facet_ranges))]
    (htstatus/ok
     {:total numFound
      :result (for [{:keys [abstract_ss]} docs] (-> abstract_ss first read-string))
      :facets (into (sorted-map) facets)})))

(comment
  (translate-query "forms:t*")
  (translate-query "text:*")
  (handle-search {:params {:q "suchen"}}))
