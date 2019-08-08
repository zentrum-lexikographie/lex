(ns zdl-lex-server.article
  (:require [clojure.data.xml :as cxml]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-server.store :as store]
            [zdl-lex-server.util :refer [->clean-map]]
            [zdl-lex-server.xml :as xml])
  (:import java.time.format.DateTimeParseException
           [java.time.temporal ChronoUnit Temporal]))

(defn xml [article]
  "Parses a lexicographic article file, returning a zipper pointing to
   the article element"
  (let [doc (-> (io/input-stream article)
                (cxml/parse :namespace-aware false))
        article-loc (-> (zip/xml-zip doc)
                        (zx/xml1-> :DWDS :Artikel))]
    (or article-loc (throw (ex-info (str article) doc)))))

(defn- distinct-sorted [vs]
  (->> (into #{} vs) (vec) (sort) (seq)))

(comment (distinct-sorted [:a :b :b :d :d :c]))

(defn- normalize-space [s]
  "Replaces whitespace runs with a single space and trims s"
  (str/trim (str/replace s #"\s+" " ")))

(defn text [s]
  (not-empty (normalize-space s)))

(defn- texts->seq [xpath ctx]
  (->> (seq (xpath ctx)) (map text) (remove nil?) (distinct) (seq)))

(defn- texts-fn [xp-expr]
  (partial texts->seq (xml/xpath-fn (str xp-expr "/text()"))))

(defn- attrs->map [attr xpath ctx]
  (some->> (for [node (-> ctx xpath seq)]
             [(.. node (getNodeName) (getLocalName))
              (.. node (attribute attr))])
           (map #(hash-map (keyword (first %)) [(second %)]))
           (apply merge-with concat)
           (map (fn [[k v]] [k (distinct v)]))
           (seq)
           (into {})))

(defn- attrs-fn [attr]
  (partial attrs->map attr (xml/xpath-fn (str ".//*[@" attr "]"))))

(defn- format-timestamp [d]
  "ISO-formatted date string"
  (t/format :iso-local-date d))

(defn- past-timestamp [s]
  "An ISO timestamp, guaranteed to be today or in the past"
  (let [now (format-timestamp (t/date))]
    (try
      (let [ts (format-timestamp (t/parse s))
            valid? (<= (compare ts now) 0)]
        (if valid? ts now))
      (catch Throwable t (timbre/warn t) now))))

(comment
  (past-timestamp "1900-01-01")
  (past-timestamp "2050-06-07")
  (past-timestamp "jfsjlj"))

(defn- past-timestamps [attrs]
  (->>
   (for [[k v] attrs]
     [k (-> (for [ts v] (past-timestamp ts)) (distinct))])
   (into {})))

(let [type (comp text str (xml/xpath-fn "@Typ/string()"))
      tranche (comp text str (xml/xpath-fn "@Tranche/string()"))
      status (comp text str (xml/xpath-fn "@Status/string()"))
      authors (attrs-fn "Autor")
      sources (attrs-fn "Quelle")
      timestamps (comp past-timestamps (attrs-fn "Zeitstempel"))
      forms (texts-fn "d:Formangabe/d:Schreibung")
      pos (texts-fn "d:Formangabe/d:Grammatik/d:Wortklasse")
      definitions (texts-fn ".//d:Definition")
      senses (texts-fn ".//d:Bedeutungsebene")
      usage-period (texts-fn ".//d:Gebrauchszeitraum")
      styles (texts-fn ".//d:Stilebene")
      colouring (texts-fn ".//d:Stilfaerbung")
      area (texts-fn ".//d:Sprachraum")
      morphological-rels (texts-fn ".//d:Verweise/d:Verweis/d:Ziellemma")
      sense-rels (texts-fn ".//d:Lesart/d:Verweise/d:Verweis/d:Ziellemma")]
  (defn excerpt [article]
    (->clean-map
     {:type (type article)
      :tranche (tranche article)
      :status (status article)
      :authors (authors article)
      :sources (sources article)
      :timestamps (timestamps article)
      :forms (forms article)
      :pos (pos article)
      :definitions (definitions article)
      :senses (senses article)
      :usage-period (usage-period article)
      :styles (styles article)
      :colouring (colouring article)
      :area (area article)
      :morphological-rels (morphological-rels article)
      :sense-rels (sense-rels article)})))

(defn field-key [n]
  (condp = n
    "_text_" :text
    (-> n
        (str/replace #"_((dts)|(dt)|(ss)|(t)|(i))$" "")
        (str/replace "_" "-")
        keyword)))

(defn field-name [k]
  (condp = k
    :text "_text_"
    (let [field-name (str/replace (name k) "-" "_")
          field-suffix (condp = k
                         :id ""
                         :language ""
                         :xml-descendent-path ""
                         :weight "_i"
                         :definitions "_t"
                         :last-modified "_dt"
                         :timestamps "_dts"
                         "_ss")]
      (str field-name field-suffix))))

(defn basic-field [[k v]]
  (if-not (nil? v) [(field-name k) (if (string? v) [v] (vec v))]))

(defn attr-field [prefix suffix attrs]
  (let [all-values (->> attrs vals (apply concat) (seq))
        typed-values (for [[type values] attrs
                           :let [type (-> type name str/lower-case)
                                 field (str prefix "_" type "_" suffix)]]
                       [field values])]
    (conj typed-values (if all-values [(str prefix "_" suffix) all-values]))))

(defn field-xml [name contents]
  {:tag :field
   :attrs {:name name}
   :content contents})

(defn- max-timestamp
  ([] nil)
  ([a] a)
  ([a b] (if (< 0 (compare a b)) a b)))

(def ^:private ^Temporal unix-epoch (t/parse "1970-01-01"))
(defn- days-since-epoch [^Temporal date]
  (.between ChronoUnit/DAYS unix-epoch date))

(def ^:private abstract-fields
  [:forms :pos :definitions :type :status :authors :sources])

(defn document [article]
  (let [excerpt (->> article xml excerpt)
        id (store/file->id article)

        timestamps (excerpt :timestamps)
        timestamp-fields (attr-field "timestamps" "dts" timestamps)

        last-modified (reduce max-timestamp (apply concat (vals timestamps)))
        last-modified-fields [[(field-name :last-modified) [last-modified]]]

        weight (try (-> last-modified t/parse days-since-epoch)
                    (catch Throwable t (timbre/warn t) 0))

        authors (excerpt :authors)
        author-fields (attr-field "authors" "ss" authors)

        sources (excerpt :sources)
        sources-fields (attr-field "sources" "ss" sources)

        basic-fields (map basic-field (dissoc excerpt :timestamps :authors :sources))

        fields (into {} (remove nil? (concat timestamp-fields
                                             last-modified-fields
                                             author-fields
                                             sources-fields
                                             basic-fields)))
        abstract (->clean-map
                  (merge {:id id
                          :last-modified last-modified
                          :timestamp (first (timestamps :Artikel))
                          :author (first (authors :Artikel))
                          :source (first (sources :Artikel))}
                         (select-keys excerpt abstract-fields)))]
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

(comment
  (let [article (first (store/article-files))
        doc (.. xml/doc-builder-factory (newDocumentBuilder) (parse article))]
    ((attrs-fn "Quelle") doc))
  (let [articles (xml/xpath-fn "/d:DWDS/d:Artikel")
        article (store/sample-article)
        doc (.. xml/doc-builder-factory (newDocumentBuilder) (parse article))]
    (for [article (seq (articles doc))]
      (excerpt article))))
