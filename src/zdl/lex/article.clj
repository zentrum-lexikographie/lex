(ns zdl.lex.article
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.zip :as zip]
            [gremid.data.xml :as dx]
            [gremid.data.xml.zip :as dx.zip]
            [zdl.lex.timestamp :as ts])
  (:import java.io.StringWriter
           java.text.Collator
           java.util.Locale))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(defn read-xml
  [in]
  (with-open [is (io/input-stream in)]
    (dx/pull-all (dx/parse is))))

(defn write-xml
  [node out]
  (let [xml-str (dx/emit-str node)
        xml-str (str/replace xml-str #"^<\?xml.+?\?>\n?" "")]
    (with-open [ow (io/writer out :encoding "UTF-8")]
      (.write ow xml-str))))

(defn write-str
  [node]
  (let [sw (StringWriter.)]
    (write-xml node sw)
    (.toString sw)))

(declare node->text)

(defn content->text
  [{:keys [content]}]
  (str/join (map node->text content)))

(defn node->text
  [node]
  (cond
    (string? node) node
    (map? node)    (condp = (:tag node)
                     ::dwds/Streichung ""
                     ::dwds/Loeschung  ""
                     ::dwds/Ziellesart ""
                     ::dwds/Ziellemma  (or (get-in node [:attrs :Anzeigeform])
                                           (content->text node))
                     (content->text node))
    :else          ""))


(defn node->orig-text
  [node]
  (cond
    (string? node) node
    (map? node)    (str/join (map node->orig-text (:content node)))
    :else          ""))

(defn normalize-text
  [s]
  (some-> s (str/replace #"\s+" " ") (str/trim) (not-empty)))

(defn text
  [node]
  (normalize-text (node->text node)))

(defn orig-text
  [node]
  (normalize-text (node->orig-text node)))

(defn zip-text
  [loc]
  (text (zip/node loc)))

(defn zip-texts
  [locs]
  (remove nil? (map zip-text locs)))

(defn hid
  [{{:keys [hidx]} :attrs :as node}]
  (str/join (cond-> [(orig-text node)] hidx (conj "#" hidx))))

(defn zip-hid
  [loc]
  (hid (zip/node loc)))

(defn zip-hids
  [locs]
  (remove nil? (map zip-hid locs)))

(def collator
  (doto (Collator/getInstance Locale/GERMAN)
    (.setStrength Collator/PRIMARY)))

(defn collation-key
  [s]
  (.getCollationKey collator s))

(defn clean-val
  [v]
  (cond
    (string? v) (normalize-text v)
    (map? v)    (not-empty v)
    (coll? v)   (not-empty (distinct v))
    :else       v))

(def clean-map-xf
  (comp (map (fn [[k v]] [k (clean-val v)]))
        (filter (fn [[_ v]] v))))

(defn clean-map
  [m]
  (into {} clean-map-xf m))

(def metadata-keys
  #{:Autor :Redakteur :Quelle :Zeitstempel})

(defn extract-metadata-attrs
  [elements]
  (->>
   (filter :attrs elements)
   (mapcat (fn [{:keys [tag attrs]}]
             (for [[ak av] attrs :when (metadata-keys ak)] [tag ak av])))
   (map (fn [[tag ak av]] [(keyword (name tag)) ak (normalize-text av)]))
   (reduce
    (fn [m [tag ak av]]
      (cond-> m
        av (update-in [ak tag] (fnil conj []) av)))
    {})))

(def timestamps-xf
  (map (fn [[k vs]] [k (map ts/past vs)])))

(defn last-modified->weight
  [last-modified]
  (or
   (try
     (some-> last-modified ts/parse ts/days-since-epoch)
     (catch Throwable t (log/warn t)))
   0))

(defn senses
  [loc]
  (for [[num sense] (map-indexed list (dx.zip/xml-> loc ::dwds/Lesart))]
    {:num    (inc num)
     :gloss  (for [gloss (dx.zip/xml-> sense ::dwds/Definition)
                   :let  [gloss-text (zip-text gloss)]
                   :when gloss-text]
               {:type (dx.zip/attr gloss :Typ) :text gloss-text})
     :senses (senses sense)}))

(def link-contexts
  #{::dwds/Formangabe ::dwds/Lesart ::dwds/Verweise})

(defn links
  [elements]
  (for [link  (map zip/xml-zip (elements ::dwds/Verweis))
        :let  [link-type  (dx.zip/attr link :Typ)
               anchor     (dx.zip/xml1-> link ::dwds/Ziellemma zip-hid)
               invisible? (dx.zip/xml1-> link (dx.zip/attr= :class "invisible"))
               external?  (#{"EtymWB" "WGd"} link-type)]
        :when (and anchor (not invisible?) (not external?))
        :let  [sense      (dx.zip/xml1-> link ::dwds/Ziellesart zip-text)]]
    {:type   (str link-type)
     :anchor anchor
     :sense  sense}))

(defn texts-of-elements
  [elements tag]
  (some->> elements tag (map zip/xml-zip) (zip-texts)))

(defn extract-article
  "Extracts article and its key data from an XML document."
  [node]
  (let [elements      (filter :tag (tree-seq :tag :content node))
        element-idx   (group-by :tag elements)
        loc           (zip/xml-zip node)
        article       (dx.zip/xml1-> loc ::dwds/DWDS ::dwds/Artikel)
        article-attrs (get (zip/node article) :attrs)
        metadata      (extract-metadata-attrs elements)
        timestamps    (into {} timestamps-xf (:Zeitstempel metadata))
        last-modified (last (sort (flatten (vals timestamps))))
        forms         (dx.zip/xml-> article ::dwds/Formangabe)
        reprs         (dx.zip/xml-> article ::dwds/Formangabe ::dwds/Schreibung)
        main-forms    (filter (dx.zip/attr= :Typ "Hauptform") forms)
        main-form     (or (first main-forms) (first forms))
        main-grammar  (dx.zip/xml1-> main-form ::dwds/Grammatik)]
    (clean-map
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
      :weight        (last-modified->weight last-modified)
      :form          (dx.zip/xml1-> main-form ::dwds/Schreibung zip-text)
      :pos           (dx.zip/xml1-> main-grammar ::dwds/Wortklasse zip-text)
      :gender        (dx.zip/xml1-> main-grammar ::dwds/Genus zip-text)
      :forms         (zip-texts reprs)
      :anchors       (zip-hids reprs)
      :senses        (senses loc)
      :definitions   (texts-of-elements element-idx ::dwds/Definition)
      :usage-period  (texts-of-elements element-idx ::dwds/Gebrauchszeitraum)
      :styles        (texts-of-elements element-idx ::dwds/Stilebene)
      :colouring     (texts-of-elements element-idx ::dwds/Stilfaerbung)
      :area          (texts-of-elements element-idx ::dwds/Sprachraum)
      :links         (links element-idx)})))

(defn desc
  [{:keys [form type status source]}]
  (format "[%s]{%s/%s/%s}" form type source status))

(defn status->color
  [status]
  (condp = (str/trim status)
    "Artikelrumpf"    "#ffcccc"
    "Lex-zur_Abgabe"  "#ffff00"
    "Red-1"           "#ffec8b"
    "Red-f"           "#aeecff"
    "wird_gestrichen" "#cccccc"
    "#ffffff"))

(comment
  (require '[zdl.lex.fs :as fs])
  (let [dir      (fs/file ".." "zdl-wb")
        articles (filter fs/file? (file-seq dir))
        articles (filter #(str/ends-with? (.getName %) ".xml") articles)]
    (mapcat (comp :links extract-article read-xml) (take 1000 articles))))
