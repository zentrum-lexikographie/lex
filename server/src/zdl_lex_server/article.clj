(ns zdl-lex-server.article
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [tick.alpha.api :as t]
            [zdl-lex-server.store :as store])
  (:import [java.time.temporal ChronoUnit Temporal]))

(defn xml [article]
  (let [doc-loc (-> article io/input-stream
                    (xml/parse :namespace-aware false) zip/xml-zip)
        article-loc (zx/xml1-> doc-loc :DWDS :Artikel)]
    (or article-loc (throw (ex-info (str article) (zip/node doc-loc))))))

(defn- normalize-space [s] (str/trim (str/replace s #"\s+" " ")))

(defn- text [loc]
  (some-> loc zx/text normalize-space))

(defn- texts [& args]
  (->> (apply zx/xml-> args) (map text) (remove empty?)
       (into #{}) (vec) (sort) (seq)))

(defn- attrs [article-loc attr]
  (let [attr-locs (zx/xml-> article-loc dz/descendants
                                 #(string? (zx/attr % attr)))
        attr-nodes (map zip/node attr-locs)
        typed-attrs (map #(hash-map (:tag %) [(get-in % [:attrs attr])])
                         attr-nodes)]
    (if (empty? typed-attrs) {} (apply merge-with concat typed-attrs))))

(defn excerpt [article-loc]
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
        timestamps (attrs article-loc :Zeitstempel)
        authors (attrs article-loc :Autor)
        sources (attrs article-loc :Quelle)
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

(def abstract-fields [:forms :pos :definitions :type :status :authors :sources])

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

(defn basic-field [[k v]]
  (if-not (nil? v) [(field-name k) (if (string? v) [v] (vec v))]))

(defn attr-field [prefix suffix attrs]
  (let [all-values (->> attrs vals (apply concat) (seq))
        typed-values (for [[type values] attrs
                           :let [type (-> type name str/lower-case)
                                 field (str prefix "_" type "_" suffix)]]
                       [field values])]
    (conj typed-values (if all-values [(str prefix "_" suffix) all-values]))))

(defn field-xml [name contents] {:tag :field :attrs {:name name} :content contents})

(defn- max-timestamp
  ([] nil)
  ([a] a)
  ([a b] (if (< 0 (compare a b)) a b)))

(def ^:private ^Temporal unix-epoch (t/parse "1970-01-01"))
(defn- days-since-epoch [^Temporal date]
  (.between ChronoUnit/DAYS unix-epoch date))

(defn document [article]
  (let [excerpt (->> article xml excerpt)
        id (store/relative-article-path article)

        timestamps (excerpt :timestamps)
        timestamp-fields (attr-field "timestamps" "dts" timestamps)

        last-modified (reduce max-timestamp (apply concat (vals timestamps)))
        last-modified-fields [[(field-name :last_modified) [last-modified]]]

        weight (-> last-modified t/parse days-since-epoch)

        author-fields (attr-field "authors" "ss" (excerpt :authors))
        sources-fields (attr-field "sources" "ss" (excerpt :sources))

        basic-fields (map basic-field (dissoc excerpt :timestamps :authors :sources))

        fields (into {} (remove nil? (concat timestamp-fields
                                             last-modified-fields
                                             author-fields
                                             sources-fields
                                             basic-fields)))
        abstract (merge {:id id :last-modified last-modified}
                        (select-keys excerpt abstract-fields))]
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

