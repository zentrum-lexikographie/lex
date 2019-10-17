(ns zdl-lex-common.article
  (:require [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-common.util :refer [->clean-map file]]
            [zdl-lex-common.xml :as xml]
            [me.raynes.fs :as fs])
  (:import java.io.File
           java.text.Collator
           java.util.Locale
           [java.time.temporal ChronoUnit Temporal]
           net.sf.saxon.s9api.QName
           net.sf.saxon.s9api.XdmItem))

(def collator (Collator/getInstance Locale/GERMAN))

(defn file->id
  ([dir]
   (partial file->id (.. (file dir) (toPath))))
  ([dir-path f]
   (str (.. dir-path (relativize (.toPath f))))))

(defn id->file
  ([dir]
   (partial id->file (file dir)))
  ([dir f]
   (file dir f)))

(defn article-xml-file?
  ([dir]
   (partial article-xml-file? (.getAbsolutePath (file dir))))
  ([dir-path ^File f]
   (let [name (.getName f)
         path (.getAbsolutePath f)]
     (and
      (str/starts-with? path dir-path)
      (.endsWith name ".xml")
      (not (.startsWith name "."))
      (not (#{"__contents__.xml" "indexedvalues.xml"} name))
      (not (.contains path ".git"))))))


(defn article-xml-files [dir]
  (let [dir (file dir)
        article-xml-file? (article-xml-file? dir)]
    (->> (file-seq dir)
         (filter article-xml-file?)
         (map file))))


(def doc->articles
  "Selects the DWDS article elements in a XML document."
  (comp seq (xml/selector "/d:DWDS/d:Artikel")))

(defn- normalize-space 
  "Replaces whitespace runs with a single space and trims s."
  [s] (str/trim (str/replace s #"\s+" " ")))

(defn text
  "Returns non-empty, whitespace-normalized string."
  [s] (not-empty (normalize-space s)))

(defn- values->seq 
  "Given an xpath selector and a evaluation context, returns a seq of distinct
   values, extracted from the selected items via `str-fn`."
  [str-fn selector ctx]
  (->> (seq (selector ctx)) (map str-fn) (remove nil?) (distinct) (seq)))

(defn- item->text [^XdmItem i]
  (text (.getStringValue i)))

(defn- texts-fn
  "Create a fn for the given xpath expression, which returns distinct
   text values."
  [xp-expr]
  (partial values->seq item->text (xml/selector xp-expr)))

(let [hidx (comp text str (xml/selector "@hidx/string()"))]
  (defn- ref-id [^XdmItem ref]
    (->> [(item->text ref) (hidx ref)]
         (remove nil?)
         (str/join \#))))

(defn- ref-ids-fn
  "Create a fn for the given xpath expression, which returns distinct
   reference identifiers."
  [xp-expr]
  (partial values->seq ref-id (xml/selector xp-expr)))

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
  (partial attrs->map attr (xml/selector (str "descendant-or-self::*[@" attr "]"))))

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

(let [refs (xml/selector ".//d:Verweis")
      ref-id (comp first (ref-ids-fn "./d:Ziellemma"))
      sense (comp first (texts-fn "./d:Ziellesart"))]
  (defn references
    "Extracts all references from an article"
    [article]
    (for [ref (refs article)]
      (->clean-map {:ref-id (ref-id ref) :sense (sense ref)}))))

(let [article-attr #(some-> (:Artikel %) (first))
      type (comp text str (xml/selector "@Typ/string()"))
      tranche (comp text str (xml/selector "@Tranche/string()"))
      status (comp text str (xml/selector "@Status/string()"))
      authors (attrs-fn "Autor")
      editors (attrs-fn "Redakteur")
      sources (attrs-fn "Quelle")
      timestamps (comp past-timestamps (attrs-fn "Zeitstempel"))
      forms (texts-fn "d:Formangabe/d:Schreibung")
      ref-ids (ref-ids-fn "d:Formangabe/d:Schreibung")
      pos (texts-fn "d:Formangabe/d:Grammatik/d:Wortklasse")
      gender (texts-fn "d:Formangabe/d:Grammatik/d:Genus")
      definitions (texts-fn ".//d:Definition")
      senses (texts-fn ".//d:Bedeutungsebene")
      usage-period (texts-fn ".//d:Gebrauchszeitraum")
      styles (texts-fn ".//d:Stilebene")
      colouring (texts-fn ".//d:Stilfaerbung")
      area (texts-fn ".//d:Sprachraum")
      references (comp seq references)]
  (defn excerpt 
    "Extracts key article data."
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
        :ref-ids (ref-ids article)
        :pos (pos article)
        :gender (gender article)
        :definitions (definitions article)
        :senses (senses article)
        :usage-period (usage-period article)
        :styles (styles article)
        :colouring (colouring article)
        :area (area article)
        :references (references article)}))))

(defn articles 
  "Extracts articles and their key data from XML files."
  ([dir]
   (partial articles (file->id dir)))
  ([file->id file]
   (let [id (file->id file)]
     (for [article (->> (xml/->dom file) (doc->articles))]
       (assoc (excerpt article) :id id :file file)))))

(defn status->color [status]
  (condp = (str/trim status)
    "Artikelrumpf" "#ffcccc"
    "Lex-zur_Abgabe" "#ffff00"
    "Red-0" "#ffec8b"
    "Red-1" "#ffec8b"
    "Red-f" "#aeecff"
    "#ffffff"))

(defn articles-in [dir]
  (mapcat (articles dir) (article-xml-files dir)))

(comment
  (->> (articles-in "../data/git/articles")
       (drop 100)
       (take 3))
  (->> (mapcat :forms (articles-in "../data/git/articles"))
       (take 1000)
       (sort collator)
       (take 100))
  (as-> (articles-in "../data/git/articles") $
       #_(remove (comp (partial = "WDG") :source) $)
       (map #(select-keys % [:forms :pos :gender :id]) $)
       #_(filter (comp (partial < 1) count :gender) $)
       (take 10 $)))
