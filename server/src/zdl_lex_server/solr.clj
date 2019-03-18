(ns zdl-lex-server.solr
  (:require [clojure.zip :as zip]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [zdl-lex-server.store :as store]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [clj-http.client :as http]
            [com.climate.claypoole :as cp]))

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
        sources (article-attrs article-loc :Quelle)]
    {:forms forms
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
     :status Status}))

(defn- solr-field-name [k]
  (let [field-name (str/replace (name k) "-" "_")
        field-suffix (condp = k
                       :definitions "_t"
                       "_ss")]
    (str field-name field-suffix)))

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

(defn document [article]
  (let [excerpt (->> article article-xml article-excerpt)
        id (store/relative-article-path article)

        timestamps (excerpt :timestamps)
        timestamp-fields (solr-attr-field "timestamp" "dts" timestamps)

        last-modified (reduce max-timestamp (apply concat (vals timestamps)))
        last-modified-fields [["last_modified_dt" [last-modified]]]

        author-fields (solr-attr-field "author" "ss" (excerpt :authors))
        source-fields (solr-attr-field "source" "ss" (excerpt :source))

        basic-fields (map solr-basic-field
                          (dissoc excerpt :timestamps :authors :sources))

        fields (into {} (remove nil? (concat timestamp-fields
                                             last-modified-fields
                                             author-fields
                                             source-fields
                                             basic-fields)))]
    {:tag :doc
     :content
     (concat [(field-xml "id" id)
              (field-xml "language" "de")
              (field-xml "xml_descendent_path" id)]
             (for [[name values] (sort fields) value (sort values)]
               (field-xml name value)))}))



(def update-conversion-concurrency (max 1 (- (cp/ncpus) 2)))
(def update-batch-size 500)
(def update-concurrency 4)

(defn update-docs [articles]
  (let [article-to-doc #(try (document %) (catch Exception e (timbre/warn e (str %))))
        documents (->> articles
                       (cp/upmap update-conversion-concurrency article-to-doc)
                       (keep identity))

        batches (partition-all update-batch-size documents)
        batch-to-doc #(assoc {:tag :add :attrs {:commitWithin "1000"}} :content %)
        update-docs (map batch-to-doc batches)]

    (cp/pdoseq
     update-concurrency
     [update update-docs]
     (timbre/info
      (http/post "http://192.168.33.10/solr/articles/update"
                 {:body (xml/emit-str update)
                  :content-type :xml
                  :basic-auth ["admin" "admin"]})))))
