(ns zdl-lex-server.solr
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [clojure.zip :as zip]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [zdl-lex-server.store :as store]
            [zdl-lex-server.env :refer [config]]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [clj-http.client :as http]
            [tick.alpha.api :as t]
            [com.climate.claypoole :as cp]
            [zdl-lex-server.bus :as bus])
  (:import java.time.temporal.ChronoUnit))

(defn article-xml [article]
  (let [doc-loc (-> article io/input-stream
                    (xml/parse :namespace-aware false) zip/xml-zip)
        article-loc (zx/xml1-> doc-loc :DWDS :Artikel)]
    (or article-loc (throw (ex-info (str article) (zip/node doc-loc))))))

(defn- normalize-space [s] (.trim (str/replace s #"\s+" " ")))

(defn- text [loc]
  (some-> loc zx/text normalize-space))

(defn- texts [& args]
  (->> (apply zx/xml-> args) (map text) (remove empty?)
       (into #{}) (vec) (sort) (seq)))

(defn- article-attrs [article-loc attr]
  (let [attr-locs (zx/xml-> article-loc dz/descendants
                                 #(string? (zx/attr % attr)))
        attr-nodes (map zip/node attr-locs)
        typed-attrs (map #(hash-map (:tag %) [(get-in % [:attrs attr])])
                         attr-nodes)]
    (if (empty? typed-attrs) {} (apply merge-with concat typed-attrs))))

(defn article-excerpt [article-loc]
  (let [forms (texts article-loc :Formangabe :Schreibung)
        pos (texts article-loc :Formangabe :Grammatik :Wortklasse)
        definitions (texts article-loc dz/descendants :Definition)
        senses (texts article-loc dz/descendants :Bedeutungsebene)
        usage-period (texts article-loc dz/descendants :Gebrauchszeitraum)
        area (texts article-loc dz/descendants :Sprachraum)
        styles (texts article-loc dz/descendants :Stilebene)
        colouring (texts article-loc dz/descendants :Stilfaerbung)
        morphological-rels (texts article-loc :Verweise :Verweis :Ziellemma)
        sense-rels (texts article-loc dz/descendants
                          :Lesart :Verweise :Verweis :Ziellemma)
        {:keys [Typ Tranche Status]} (-> article-loc zip/node :attrs)
        timestamps (article-attrs article-loc :Zeitstempel)
        authors (article-attrs article-loc :Autor)
        sources (article-attrs article-loc :Quelle)
        excerpt {:forms forms
                 :pos pos
                 :definitions definitions
                 :senses senses
                 :usage-period usage-period
                 :styles styles
                 :colouring colouring
                 :area area
                 :morphological-rels morphological-rels
                 :sense-rels sense-rels
                 :timestamps timestamps
                 :authors authors
                 :sources sources
                 :type Typ
                 :tranche Tranche
                 :status Status}]
    (apply dissoc excerpt (for [[k v] excerpt :when (nil? v)] k))))

(def article-abstract-fields [:forms :pos :definitions :type :status :authors])

(defn solr-field-name [k]
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

(defn solr-field-key [n]
  (-> n
      (str/replace #"_((dts)|(dt)|(ss)|(t)|(i))$" "")
      (str/replace "_" "-")
      keyword))

(defn- solr-basic-field [[k v]]
  (if-not (nil? v) [(solr-field-name k) (if (string? v) [v] (vec v))]))

(defn- solr-attr-field [prefix suffix attrs]
  (let [all-values (->> attrs vals (apply concat) (seq))
        typed-values (for [[type values] attrs
                           :let [type (-> type name (.toLowerCase))
                                 field (str prefix "_" type "_" suffix)]]
                       [field values])]
    (conj typed-values (if all-values [(str prefix "_" suffix) all-values]))))

(defn- field-xml [name contents] {:tag :field :attrs {:name name} :content contents})

(defn- max-timestamp
  ([] nil)
  ([a] a)
  ([a b] (if (< 0 (compare a b)) a b)))


(def unix-epoch (t/parse "1970-01-01"))
(defn days-since-epoch [date]
  (.between ChronoUnit/DAYS unix-epoch date))

(defn document [article]
  (let [excerpt (->> article article-xml article-excerpt)
        id (store/relative-article-path article)

        timestamps (excerpt :timestamps)
        timestamp-fields (solr-attr-field "timestamps" "dts" timestamps)

        last-modified (reduce max-timestamp (apply concat (vals timestamps)))
        last-modified-fields [[(solr-field-name :last_modified) [last-modified]]]

        weight (-> last-modified t/parse days-since-epoch)

        author-fields (solr-attr-field "authors" "ss" (excerpt :authors))
        source-fields (solr-attr-field "source" "ss" (excerpt :source))

        basic-fields (map solr-basic-field
                          (dissoc excerpt :timestamps :authors :sources))

        fields (into {} (remove nil? (concat timestamp-fields
                                             last-modified-fields
                                             author-fields
                                             source-fields
                                             basic-fields)))
        abstract (merge {:id id :last-modified last-modified}
                        (select-keys excerpt article-abstract-fields))]
    {:tag :doc
     :content
     (concat [(field-xml "id" id)
              (field-xml "language" "de")
              (field-xml "xml_descendent_path" id)
              (field-xml "weight_i" (str weight))
              (field-xml "abstract_ss" (pr-str abstract))]
             (for [[name values] (sort fields) value (sort values)]
               (field-xml name value)))}))


(def solr-req (config :solr-req))

(def solr-query-url (str (config :solr-base) "/" (config :solr-core) "/query"))

(defn solr-query [params]
  (http/get solr-query-url (merge solr-req {:query-params params :as :json})))


(def solr-suggest-url (str (config :solr-base) "/" (config :solr-core) "/suggest"))

(defn solr-suggest [name q]
  (let [params {"suggest.dictionary" name "suggest.q" q}]
    (http/get solr-suggest-url (merge solr-req {:query-params params :as :json}))))

(def solr-update-pool (cp/threadpool 4))
(def solr-update-url (str (config :solr-base) "/" (config :solr-core) "/update"))

(defn solr-updates [reqs]
  (cp/pdoseq
   solr-update-pool
   [req reqs]
   (->> (merge solr-req req) (http/post solr-update-url) timbre/info)))

(def solr-update-conversion-pool (cp/threadpool (max 1 (- (cp/ncpus) 2))))
(def solr-update-batch-size 500)

(defn commit-optimize []
  (solr-updates [{:body "<update><commit/><optimize/></update>"
                  :content-type :xml}]))

(defn update-docs [articles]
  (let [article-to-doc #(try (document %) (catch Exception e (timbre/warn e (str %))))
        documents (->> articles
                       (cp/upmap solr-update-conversion-pool article-to-doc)
                       (keep identity))

        batches (partition-all solr-update-batch-size documents)
        batch-to-doc #(array-map :tag :add :attrs {:commitWithin "1000"} :content %)
        update-docs (map batch-to-doc batches)

        doc-to-req #(array-map :body (xml/emit-str %) :content-type :xml)
        update-reqs (map doc-to-req update-docs)]

    (solr-updates update-reqs)))

(defn delete-docs [articles]
  (let [ids (map store/relative-article-path articles)
        id-elements (map #(array-map :tag :id :content %) ids)

        batches (partition-all solr-update-batch-size id-elements)
        batch-to-doc #(array-map :tag :delete :content %)
        update-docs (map batch-to-doc batches)

        doc-to-req #(array-map :body (xml/emit-str %) :content-type :xml)
        update-reqs (map doc-to-req update-docs)]
    (solr-updates update-reqs)))

(defstate index-changes
  :start (let [changes-ch (async/tap bus/git-changes-mult (async/chan))]
           (async/go-loop []
             (when-let [changes (async/<! changes-ch)]
               (let [articles (filter store/article-file? changes)
                     modified (filter fs/exists? articles)
                     deleted (remove fs/exists? articles)]
                 (update-docs modified)
                 (delete-docs deleted))
               (recur)))
           changes-ch)
  :stop (do
          (async/untap bus/git-changes-mult index-changes)
          (async/close! index-changes)))
