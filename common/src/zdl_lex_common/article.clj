(ns zdl-lex-common.article
  (:require [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-common.util :refer [->clean-map]]
            [zdl-lex-common.xml :as xml])
  (:import [java.time.temporal ChronoUnit Temporal]
           net.sf.saxon.s9api.QName
           net.sf.saxon.s9api.XdmItem))

(defn status->color [status]
  (condp = (str/trim status)
    "Artikelrumpf" "#ffcccc"
    "Lex-zur_Abgabe" "#ffff00"
    "Red-0" "#ffec8b"
    "Red-1" "#ffec8b"
    "Red-f" "#ccffcc"
    "#ffffff"))

(def doc->articles
  "Selects the DWDS article elements in a XML document."
  (comp seq (xml/xpath-fn "/d:DWDS/d:Artikel")))

(defn- normalize-space 
  "Replaces whitespace runs with a single space and trims s."
  [s] (str/trim (str/replace s #"\s+" " ")))

(defn text
  "Returns non-empty, whitespace-normalized string."
  [s] (not-empty (normalize-space s)))

(defn- texts->seq 
  "Given an xpath fn and a evaluation context, returns a seq of distinct
   text values."
  [xpath ctx]
  (->> (seq (xpath ctx))
       (map (fn [^XdmItem i] (.getStringValue i)))
       (map text)
       (remove nil?)
       (distinct)
       (seq)))

(defn- texts-fn
  "Create a fn for the given xpath expression, which returns distinct
   text values."
  [xp-expr]
  (partial texts->seq (xml/xpath-fn xp-expr)))

(defn- attrs->map
  "Returns a mapping of tagname to distinct values for the given attribute
   by evaluating the given xpath fn in the given context."
  [attr xpath ctx]
  (some->> (for [node (-> ctx xpath seq)]
             [(.. node (getNodeName) (getLocalName))
              (.. node (getAttributeValue (QName. attr)))])
           (map #(hash-map (keyword (first %)) [(second %)]))
           (apply merge-with concat)
           (map (fn [[k v]] [k (distinct v)]))
           (seq)
           (into {})))

(defn- attrs-fn 
  "Creates a fn for the given attribute, returning mappings of tagnames to
   distinct attribute values."
  [attr]
  (partial attrs->map attr (xml/xpath-fn (str "descendant-or-self::*[@" attr "]"))))

(defn- format-timestamp
  "ISO-formats a date string."
  [d]
  (t/format :iso-local-date d))

(defn- past-timestamp
  "Yields a ISO timestamp, guaranteed to be today or in the past."
  [s]
  (let [now (format-timestamp (t/date))]
    (try
      (let [ts (format-timestamp (t/parse s))
            valid? (<= (compare ts now) 0)]
        (if valid? ts now))
      (catch Throwable t now))))

(defn- past-timestamps
  "Maps attribute timestamp values via `past-timestamp`."
  [attrs]
  (->>
   (for [[k v] attrs]
     [k (-> (for [ts v] (past-timestamp ts)) (distinct))])
   (into {})))

(defn- max-timestamp
  "A reducer fn, yielding the maximum timestamp"
  ([] nil)
  ([a] a)
  ([a b] (if (< 0 (compare a b)) a b)))

(let [^Temporal unix-epoch (t/parse "1970-01-01")]
  (defn- days-since-epoch [^Temporal date]
    "The number of days since the UNIX epoch for a given date."
    (.between ChronoUnit/DAYS unix-epoch date)))

(let [article-attr #(some-> (:Artikel %) (first))
      type (comp text str (xml/xpath-fn "@Typ/string()"))
      tranche (comp text str (xml/xpath-fn "@Tranche/string()"))
      status (comp text str (xml/xpath-fn "@Status/string()"))
      authors (attrs-fn "Autor")
      editors (attrs-fn "Redakteur")
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
  (defn excerpt
    "Extracts key data from an article."
    [article]
    (let [authors (authors article)
          sources (sources article)
          editors (editors article)
          timestamps (timestamps article)
          last-modified (reduce max-timestamp (apply concat (vals timestamps)))
          weight (try (or (some-> last-modified t/parse days-since-epoch) 0)
                      (catch Throwable t (timbre/warn t) 0))]
      (->clean-map
       {:timestamp (article-attr timestamps)
        :timestamps timestamps
        :last-modified last-modified
        :weight weight
        :type (type article)
        :tranche (tranche article)
        :status (status article)
        :author (article-attr authors)
        :authors authors
        :editor (article-attr editors)
        :editors editors
        :source (article-attr sources)
        :sources sources
        :forms (forms article)
        :pos (pos article)
        :definitions (definitions article)
        :senses (senses article)
        :usage-period (usage-period article)
        :styles (styles article)
        :colouring (colouring article)
        :area (area article)
        :morphological-rels (morphological-rels article)
        :sense-rels (sense-rels article)}))))
