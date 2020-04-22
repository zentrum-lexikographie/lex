(ns zdl-lex-common.article
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [zdl-lex-common.article.fs :as afs]
            [zdl-lex-common.article.refs :as arefs]
            [zdl-lex-common.article.validate :as av]
            [zdl-lex-common.article.xml :as axml]
            [zdl-lex-common.timestamp :as ts]
            [zdl-lex-common.util :refer [->clean-map relativize]]
            [zdl-xml.util :as xml])
  (:import java.text.Collator
           java.util.Locale
           net.sf.saxon.s9api.QName))

(def collator (Collator/getInstance Locale/GERMAN))

(defn- texts-fn
  "Create a fn for the given xpath expression, which returns distinct
   text values."
  [xp-expr]
  (let [select-contexts (comp seq (xml/selector xp-expr))]
    (fn [node]
      (-> node select-contexts axml/texts))))

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

(defn- past-timestamps
  "Maps attribute timestamp values via `past-timestamp`."
  [attrs]
  (->>
   (for [[k v] attrs]
     [k (-> (for [ts v] (ts/past ts)) (distinct))])
   (into {})))

(let [article-attr #(some-> (:Artikel %) (first))
      type (comp xml/text str (xml/selector "@Typ/string()"))
      tranche (comp xml/text str (xml/selector "@Tranche/string()"))
      status (comp xml/text str (xml/selector "@Status/string()"))
      authors (attrs-fn "Autor")
      editors (attrs-fn "Redakteur")
      sources (attrs-fn "Quelle")
      timestamps (comp past-timestamps (attrs-fn "Zeitstempel"))
      main-forms (texts-fn "d:Formangabe[@Typ='Hauptform']/d:Schreibung")
      forms (comp axml/texts axml/select-surface-forms)
      ref-ids (comp seq (partial remove nil?) (partial map arefs/id)
                    seq (xml/selector "./d:Formangabe/d:Schreibung"))
      pos (texts-fn "d:Formangabe/d:Grammatik/d:Wortklasse")
      gender (texts-fn "d:Formangabe/d:Grammatik/d:Genus")
      definitions (texts-fn ".//d:Definition")
      senses (texts-fn ".//d:Bedeutungsebene")
      usage-period (texts-fn ".//d:Gebrauchszeitraum")
      styles (texts-fn ".//d:Stilebene")
      colouring (texts-fn ".//d:Stilfaerbung")
      area (texts-fn ".//d:Sprachraum")]
  (defn excerpt
    "Extracts key article data."
    [article]
    (let [forms (forms article)
          authors (authors article)
          sources (sources article)
          editors (editors article)
          timestamps (timestamps article)
          last-modified (some->> (vals timestamps) (apply concat) (sort) (last))
          weight (try (or (some-> last-modified ts/parse ts/days-since-epoch) 0)
                      (catch Throwable t (log/warn t) 0))]
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
        :form (or (first (main-forms article)) (first forms))
        :forms forms
        :ref-ids (ref-ids article)
        :pos (pos article)
        :gender (gender article)
        :definitions (definitions article)
        :senses (senses article)
        :usage-period (usage-period article)
        :styles (styles article)
        :colouring (colouring article)
        :area (area article)}))))

(defn articles
  "Extracts articles and their key data from XML files."
  [dir]
  (let [f->id (comp str (partial relativize dir))]
    (for [f (afs/files dir) :let [id (f->id f)]
          a (-> f xml/->xdm axml/doc->articles)]
       (merge {:id id :file f} (excerpt a) (av/check f a)))))

(defn status->color
  [status]
  (condp = (str/trim status)
    "Artikelrumpf" "#ffcccc"
    "Lex-zur_Abgabe" "#ffff00"
    "Red-0" "#ffec8b"
    "Red-1" "#ffec8b"
    "Red-f" "#aeecff"
    "#ffffff"))

(comment
  (->> (articles "../data/git")
       (filter (comp (partial < 1) count :definitions))
       #_(drop 100)
       (map (juxt :id :ref-ids))
       #_(mapcat :ref-ids)
       (take 100)
       #_(sort collator))
  (->> (articles "../../zdl-wb")
       #_(filter (comp #_:sense seq :references))
       #_(drop 1000)
       #_(map (juxt :form :references))
       (take 3))
  (->> (articles "../../zdl-wb")
       (remove (comp #{"Red-2" "Red-f"} :status))
       (filter :errors)
       (map #(select-keys % [:form #_:pos #_:gender #_:id #_:status :errors]))
       (take 10)
       (time))
  (as-> (articles "../../zdl-wb") $
       #_(remove (comp (partial = "WDG") :source) $)
       (map #(select-keys % [:form :pos :gender :id]) $)
       #_(filter (comp (partial < 1) count :gender) $)
       (take 10 $)))
