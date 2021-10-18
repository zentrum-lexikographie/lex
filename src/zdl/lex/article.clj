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
  (Collator/getInstance Locale/GERMAN))

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
  [node]
  (->>
   (filter :attrs (tree-seq :tag :content node))
   (mapcat (fn [{:keys [tag attrs]}]
             (for [[ak av] attrs :when (metadata-keys ak)] [tag ak av])))
   (map (fn [[tag ak av]] [(keyword (name tag)) ak (axml/normalize-text av)]))
   (reduce
    (fn [m [tag ak av]]
      (cond-> m
        av (update-in [ak tag] (fnil conj []) av)))
    {})))

(defnp extract-metadata
  [article]
  (let [article-attrs (get article :attrs)
        metadata      (extract-metadata-attrs article)
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
     :last-modified last-modified}))

(defnp descendants-by-tag
  [loc tag]
  (filter #(= tag (:tag (zip/node %))) (dz/descendants loc)))

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
  [loc]
  (for [link  (descendants-by-tag loc ::dwds/Verweis)
        :let  [anchor (zx/xml1-> link ::dwds/Ziellemma axml/zip-hid)]
        :when anchor
        :let  [link-ancestors (zx/xml-> link dz/ancestors zip/node)
               [context] (filter (comp link-contexts :tag) link-ancestors)
               context (some-> context :tag name)]
        :when context
        :let  [link-type (zx/attr link :Typ)
               sense (zx/xml1-> link ::dwds/Ziellesart axml/zip-text)]]
    {:context context
     :type    link-type
     :anchor  anchor
     :sense   sense}))

(defnp extract-lex-data
  [loc]
  (let [forms        (zx/xml-> loc ::dwds/Formangabe)
        reprs        (for [f forms] (zx/xml1-> f ::dwds/Schreibung))
        [main-form]  (or (filter (zx/attr= :Typ "Hauptform") forms) forms)
        main-grammar (zx/xml1-> main-form ::dwds/Grammatik)]
    {:form         (zx/xml1-> main-form ::dwds/Schreibung axml/zip-text)
     :pos          (zx/xml1-> main-grammar ::dwds/Wortklasse axml/zip-text)
     :gender       (zx/xml1-> main-grammar ::dwds/Genus axml/zip-text)
     :forms        (axml/zip-texts reprs)
     :senses       (senses loc)
     :definitions  (axml/zip-texts
                    (descendants-by-tag loc ::dwds/Definition))
     :usage-period (axml/zip-texts
                    (descendants-by-tag loc ::dwds/Gebrauchszeitraum))
     :styles       (axml/zip-texts
                    (descendants-by-tag loc ::dwds/Stilebene))
     :colouring    (axml/zip-texts
                    (descendants-by-tag loc ::dwds/Stilfaerbung))
     :area         (axml/zip-texts
                    (descendants-by-tag loc ::dwds/Sprachraum))
     :links        (links loc)
     :anchors      (axml/zip-hids reprs)}))

(defnp check-for-errors
  [article f]
  {:errors
   (cond-> []
     (seq (av/check-typography article)) (conj "Typographie")
     (seq (av/rng-validate f))           (conj "Schema")
     (seq (av/sch-validate f))           (conj "Schematron"))})

(defnp assoc-weight
  [{:keys [last-modified] :as article}]
  (assoc
   article :weight
   (try
     (or (some-> last-modified
                 ts/parse
                 ts/days-since-epoch)
         0)
     (catch Throwable t
       (log/warn t)
       0))))

(defnp extract-articles
  "Extracts articles and their key data from an XML file."
  [{:keys [id file] :as article-file} & opts]
  (let [{:keys [lex-data? errors?] :or {lex-data? true errors? true}} opts]
    (for [loc  (zx/xml-> (zip/xml-zip (axml/read-xml file)) ::dwds/Artikel)
          :let [article (zip/node loc)]]
      (cond-> article-file
        :always   (merge (extract-metadata article))
        lex-data? (merge (extract-lex-data loc))
        errors?   (merge (check-for-errors article file))
        :always   (assoc-weight)
        :always   (clean-map)))))

(defn describe-article-file
  [dir f]
  {:id (str (relativize dir f)) :file f})

(defnp article-files
  "Extracts articles and their key data from XML files in a dir."
  [dir]
  (map #(describe-article-file dir %) (article/files dir)))

(defn status->color
  [status]
  (condp = (str/trim status)
    "Artikelrumpf"   "#ffcccc"
    "Lex-zur_Abgabe" "#ffff00"
    "Red-0"          "#ffec8b"
    "Red-1"          "#ffec8b"
    "Red-f"          "#aeecff"
    "#ffffff"))

(comment
  (tufte/add-basic-println-handler! {})
  (profile
   {}
   (->>
    (article-files "../../zdl-wb/Duden-1999")
    (mapcat #(extract-articles % :errors? false))
    #_(take 10000)
    (filter (comp seq :links))
    (take 10)))

  (time (map :links (take 30 (drop 200 (articles )))))
  (->> (articles "../../zdl-wb")
       (filter (comp (partial < 1) count :definitions))
       #_(drop 100)
       (map (juxt :id :anchors :links))
       #_(mapcat :ref-ids)
       (take 100)
       #_(sort collator))
  (->> (articles "../../zdl-wb")
       #_(filter (comp #_:sense seq :references))
       (drop 1000)
       #_(map (juxt :form :references))
       (filter :senses)
       (drop 200)
       (map (juxt :forms :gender :pos :status :senses))
       #_(drop 200)
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
