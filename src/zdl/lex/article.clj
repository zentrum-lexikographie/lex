(ns zdl.lex.article
  (:require [clojure.data.xml :as dx]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.zip :as zip]
            [taoensso.tufte :as tufte :refer [defnp profile]]
            [zdl.lex.article :as article]
            [zdl.lex.article.validate :as av]
            [zdl.lex.article.xml :as axml]
            [zdl.lex.fs :refer [file relativize]]
            [zdl.lex.timestamp :as ts])
  (:import java.io.File
           java.text.Collator
           java.util.Locale))

(defn article-file?
  [^File f]
  (let [name (.getName f)
        path (.getAbsolutePath f)]
    (and
     (.endsWith name ".xml")
     (not (.startsWith name "."))
     (not (#{"__contents__.xml" "indexedvalues.xml"} name))
     (not (.contains path ".git")))))

(defn files
  [dir]
  (->> dir file file-seq (filter article-file?) (map file)))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(def collator
  (doto (Collator/getInstance Locale/GERMAN)
    (.setStrength Collator/PRIMARY)))

(defn collation-key
  [s]
  (.getCollationKey collator s))

(defn clean-val
  [v]
  (cond
    (string? v) (axml/normalize-text v)
    (map? v)    (not-empty v)
    (coll? v)   (not-empty (distinct v))
    :else       v))

(def clean-map-xf
  (comp (map (fn [[k v]] [k (clean-val v)]))
        (filter (fn [[_ v]] v))))

(defnp clean-map
  [m]
  (into {} clean-map-xf m))

(def metadata-keys
  #{:Autor :Redakteur :Quelle :Zeitstempel})

(defnp extract-metadata-attrs
  [elements]
  (->>
   (filter :attrs elements)
   (mapcat (fn [{:keys [tag attrs]}]
             (for [[ak av] attrs :when (metadata-keys ak)] [tag ak av])))
   (map (fn [[tag ak av]] [(keyword (name tag)) ak (axml/normalize-text av)]))
   (reduce
    (fn [m [tag ak av]]
      (cond-> m
        av (update-in [ak tag] (fnil conj []) av)))
    {})))

(defnp last-modified->weight
  [last-modified]
  (or
   (try
     (some-> last-modified ts/parse ts/days-since-epoch)
     (catch Throwable t (log/warn t)))
   0))

(defnp extract-metadata
  [article elements]
  (let [article-attrs (get article :attrs)
        metadata      (extract-metadata-attrs elements)
        timestamps    (into {} (map (fn [[k vs]] [k (map ts/past vs)]))
                            (get metadata :Zeitstempel))
        last-modified (last (sort (flatten (vals timestamps))))]
    {:type          (get article-attrs :Typ)
     :status        (get article-attrs :Status)
     :source        (get article-attrs :Quelle)
     :author        (get article-attrs :Autor)
     :editor        (get article-attrs :Redakteur)
     :timestamp     (first (get timestamps :Artikel))
     :tranche       (get article-attrs :Tranche)
     :provenance    (get article-attrs :Erstfassung)
     :authors       (get metadata :Autor)
     :editors       (get metadata :Redakteur)
     :sources       (get metadata :Quelle)
     :timestamps    timestamps
     :last-modified last-modified
     :weight        (last-modified->weight last-modified)}))

(defnp senses
  [loc]
  (for [[num sense] (map-indexed list (zx/xml-> loc dz/children ::dwds/Lesart))]
    {:num    (inc num)
     :gloss  (for [gloss (zx/xml-> sense ::dwds/Definition)
                   :let  [gloss-text (axml/zip-text gloss)]
                   :when gloss-text]
               {:type (zx/attr gloss :Typ) :text gloss-text})
     :senses (senses sense)}))

(def link-contexts
  #{::dwds/Formangabe ::dwds/Lesart ::dwds/Verweise})

(defnp links
  [elements]
  (for [link  (map zip/xml-zip (elements ::dwds/Verweis))
        :let  [anchor (zx/xml1-> link ::dwds/Ziellemma axml/zip-hid)]
        :when anchor
        :let  [link-type (zx/attr link :Typ)
               sense (zx/xml1-> link ::dwds/Ziellesart axml/zip-text)]]
    {:type    link-type
     :anchor  anchor
     :sense   sense}))

(defn texts-of-elements
  [elements tag]
  (some->> elements tag (map zip/xml-zip) (axml/zip-texts)))

(defn extract-grammar-data
  [loc]
  (let [forms        (zx/xml-> loc ::dwds/Formangabe)
        reprs        (for [form forms
                           repr (zx/xml-> form ::dwds/Schreibung)]
                       repr)
        [main-form]  (or (seq (filter (zx/attr= :Typ "Hauptform") forms)) forms)
        main-grammar (zx/xml1-> main-form ::dwds/Grammatik)]
    {:form    (zx/xml1-> main-form ::dwds/Schreibung axml/zip-text)
     :pos     (zx/xml1-> main-grammar ::dwds/Wortklasse axml/zip-text)
     :gender  (zx/xml1-> main-grammar ::dwds/Genus axml/zip-text)
     :forms   (axml/zip-texts reprs)
     :anchors (axml/zip-hids reprs)}))

(defnp extract-lex-data
  [article elements]
  (let [loc          (zip/xml-zip article)
        element-idx  (group-by :tag elements)]
    (merge
     (extract-grammar-data loc)
     {:senses       (senses loc)
      :definitions  (texts-of-elements element-idx ::dwds/Definition)
      :usage-period (texts-of-elements element-idx ::dwds/Gebrauchszeitraum)
      :styles       (texts-of-elements element-idx ::dwds/Stilebene)
      :colouring    (texts-of-elements element-idx ::dwds/Stilfaerbung)
      :area         (texts-of-elements element-idx ::dwds/Sprachraum)
      :links        (links element-idx)})))

(defnp check-for-errors
  [article f]
  {:errors
   (cond-> []
     (seq (av/check-typography article)) (conj "Typographie")
     (seq (av/rng-validate f))           (conj "Schema")
     (seq (av/sch-validate f))           (conj "Schematron"))})

(defnp extract-articles
  "Extracts articles and their key data from an XML file."
  [doc & {:as opts}]
  (try
    (let [file      (get opts :file)
          lex-data? (get opts :lex-data? true)
          errors?   (get opts :errors? true)
          articles  (filter (comp #{::dwds/Artikel} :tag) (get doc :content))]
      (vec
       (for [article articles]
         (let [elements (filter :tag (tree-seq :tag :content article))]
           (cond-> {}
             :always            (merge (extract-metadata article elements))
             lex-data?          (merge (extract-lex-data article elements))
             (and file errors?) (merge (check-for-errors article file))
             :always            (clean-map))))))
    (catch Throwable t
      (log/warnf t "Error extracting data from %s"
                 (or (get opts :file) "<XML/>")))))

(defn file->metadata
  [dir f]
  {:id (str (relativize dir f)) :file f})

(defn extract-articles-from-file
  [dir f]
  (let [file-data (file->metadata dir f)]
    (into []
          (map #(merge file-data %))
          (extract-articles (axml/read-xml f) :file f))))

(defnp extract-articles-from-files
  "Extracts articles and their key data from XML files in a dir."
  ([dir]
   (extract-articles-from-files dir (article/files dir)))
  ([dir files]
   (flatten (pmap #(extract-articles-from-file dir %) files))))

(defn status->color
  [status]
  (condp = (str/trim status)
    "Artikelrumpf"    "#ffcccc"
    "Lex-zur_Abgabe"  "#ffff00"
    "Red-0"           "#ffec8b"
    "Red-1"           "#ffec8b"
    "Red-f"           "#aeecff"
    "wird_gestrichen" "#cccccc"
    "#ffffff"))

(comment
  (mapcat :links (extract-articles-from-files (file "data/git")))
  (tufte/add-basic-println-handler! {})
  (profile
   {}
   (->>
    (article-files "../zdl-wb")
    (map #(select-keys % [:author :last-modified]))
    (filter :author)
    (filter #(> 0 (compare "2020" (:last-modified %))))
    (map :author)
    (frequencies))))
