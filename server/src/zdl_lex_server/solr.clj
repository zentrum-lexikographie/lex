(ns zdl-lex-server.solr
  (:require [clj-http.client :as http]
            [clojure.core.async :as async]
            [clojure.data.xml :as xml]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [com.climate.claypoole :as cp]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-server.article :as article]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store])
  (:import [java.time.temporal ChronoUnit Temporal]))

(defn field-key [n]
  (-> n
      (str/replace #"_((dts)|(dt)|(ss)|(t)|(i))$" "")
      (str/replace "_" "-")
      keyword))

(defn field-name [k]
  (let [field-name (str/replace (name k) "-" "_")
        field-suffix (condp = k
                       :id ""
                       :language ""
                       :xml_descendent_path ""
                       :weight "_i"
                       :definitions "_t"
                       :last_modified "_dt"
                       :timestamps "_dts"
                       "_ss")]
    (str field-name field-suffix)))

(defn- basic-field [[k v]]
  (if-not (nil? v) [(field-name k) (if (string? v) [v] (vec v))]))

(defn- attr-field [prefix suffix attrs]
  (let [all-values (->> attrs vals (apply concat) (seq))
        typed-values (for [[type values] attrs
                           :let [type (-> type name str/lower-case)
                                 field (str prefix "_" type "_" suffix)]]
                       [field values])]
    (conj typed-values (if all-values [(str prefix "_" suffix) all-values]))))

(defn- field-xml [name contents] {:tag :field :attrs {:name name} :content contents})

(defn- max-timestamp
  ([] nil)
  ([a] a)
  ([a b] (if (< 0 (compare a b)) a b)))

(def ^:private ^Temporal unix-epoch (t/parse "1970-01-01"))
(defn- days-since-epoch [^Temporal date]
  (.between ChronoUnit/DAYS unix-epoch date))

(defn document [article]
  (let [excerpt (->> article article/xml article/excerpt)
        id (store/relative-article-path article)

        timestamps (excerpt :timestamps)
        timestamp-fields (attr-field "timestamps" "dts" timestamps)

        last-modified (reduce max-timestamp (apply concat (vals timestamps)))
        last-modified-fields [[(field-name :last_modified) [last-modified]]]

        weight (-> last-modified t/parse days-since-epoch)

        author-fields (attr-field "authors" "ss" (excerpt :authors))
        source-fields (attr-field "source" "ss" (excerpt :source))

        basic-fields (map basic-field (dissoc excerpt :timestamps :authors :sources))

        fields (into {} (remove nil? (concat timestamp-fields
                                             last-modified-fields
                                             author-fields
                                             source-fields
                                             basic-fields)))
        abstract (merge {:id id :last-modified last-modified}
                        (select-keys excerpt article/abstract-fields))]
    {:tag :doc
     :content
     (concat [(field-xml "id" id)
              (field-xml "language" "de")
              (field-xml "time_l" (str (System/currentTimeMillis)))
              (field-xml "xml_descendent_path" id)
              (field-xml "weight_i" (str weight))
              (field-xml "abstract_ss" (pr-str abstract))]
             (for [[name values] (sort fields) value (sort values)]
               (field-xml name value)))}))

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

(def ^:private update-pool (cp/threadpool 8))
(def ^:private update-batch-size 2000)

(defn batch-update [updates]
  (cp/pfor update-pool [upd updates]
           (req (merge {:method :post
                        :url (url "/update")
                        :query-params {:wt "json"}
                        :as :json} upd))))

(def commit-optimize
  (partial batch-update [{:body "<update><commit/><optimize/></update>"
                          :content-type :xml}]))

(defn update-articles [action article->el articles]
  (batch-update (->> articles
                     (map article->el)
                     (keep identity)
                     (partition-all update-batch-size)
                     (map #(array-map :tag action
                                      :attrs {:commitWithin "1000"}
                                      :content %))
                     (map #(array-map :body (xml/emit-str %)
                                      :content-type :xml)))))

(def add-articles
  (partial update-articles
           :add
           #(try (document %) (catch Exception e (timbre/warn e (str %))))))

(def delete-articles
  (partial update-articles
           :delete
           (comp #(array-map :tag :id :content %)
                 store/relative-article-path)))

(defn purge-articles [before-time]
  (update-articles :delete
                   #(array-map :tag :query :content %)
                   [(format "time_l:[* TO %s}" before-time)]))

(defn sync-articles []
  (let [sync-start (System/currentTimeMillis)
        articles (store/article-files)]
    (if-not (empty? (add-articles articles))
      (purge-articles sync-start)
      (commit-optimize))
    articles))
