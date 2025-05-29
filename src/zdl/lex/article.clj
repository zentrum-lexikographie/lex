(ns zdl.lex.article
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [gremid.xml :as gx]
   [tick.core :as t])
  (:import
   (java.text Collator Normalizer Normalizer$Form)
   (java.util Locale)))

(def collator
  (doto (Collator/getInstance Locale/GERMAN)
    (.setStrength Collator/PRIMARY)))

(defn collation-key
  [s]
  (.getCollationKey collator s))

(defn read-xml
  [in]
  (with-open [is (io/input-stream in)]
    (-> is (gx/read-events) (gx/events->node :location-info? true))))

(defn ->str
  [node]
  (->
   (with-out-str (->> node gx/node->events (gx/write-events *out*)))
   (str/replace #"^<\?xml.+?\?>\n?" "")))

(defn write-xml
  [node out]
  (with-open [ow (io/writer out :encoding "UTF-8")]
    (spit ow (->str node))))

(defn node->texts
  [{:keys [tag content] :as node}]
  (if (string? node)
    (list node)
    (condp = tag
      :Streichung nil
      :Loeschung  nil
      :Ziellesart nil
      :Ziellemma  (list (or (get-in node [:attrs :Anzeigeform])
                            (str/join (mapcat node->texts content))))
      (list (str/join (mapcat node->texts content))))))

(defn normalize-texts
  [s]
  (some-> s (str/replace #"\s+" " ") (str/trim) (not-empty) (list)))

(def attrs
  (comp (partial mapcat normalize-texts) gx/attrs))

(def attr
  (comp first attrs))

(defn texts
  [node]
  (mapcat normalize-texts (node->texts node)))

(defn hids
  [{{:keys [hidx]} :attrs :as node}]
  (normalize-texts (cond-> (gx/text node) hidx (str "#" hidx))))

(defn parse-gloss
  [gloss]
  (when-let [text (first (texts gloss))]
    (let [type (gx/attr :Typ gloss)]
      (cond-> {:text text} type (assoc :type type)))))

(declare parse-senses)

(defn parse-sense
  [num sense]
  (let [subsenses (parse-senses sense)
        glosses   (into []
                        (mapcat parse-gloss)
                        (filter (gx/tag-pred :Definition) (:content sense)))]
    (cond-> {:num (inc num)}
      (seq glosses)   (assoc :gloss glosses)
      (seq subsenses) (assoc :senses (vec subsenses)))))

(defn parse-senses
  [{:keys [content]}]
  (map-indexed parse-sense (filter (gx/tag-pred :Lesart) content)))

(defn senses
  [node]
  (mapcat parse-senses (gx/elements :Artikel node)))

(defn main-form?
  [form]
  (= "Hauptform" (attr :Typ form)))

(defn parse-link
  [link]
  (let [type (gx/attr :Typ link)]
    (when (and (not (#{"EtymWB" "WGd"} type))
               (not (= "invisible" (gx/attr :class link)) ))
      (when-let [anchor (first (mapcat hids (gx/elements :Ziellemma link)))]
        (let [sense (first (mapcat texts (gx/elements :Ziellesart link)))]
          (list (cond-> {:anchor anchor}
                  type  (assoc :type type)
                  sense (assoc :sense sense))))))))

(defn parse-article
  "Extracts article and its key data from an XML document."
  [article]
  (let [last-modified (or
                       (->> (gx/traverse article)
                            (mapcat (partial attrs :Zeitstempel))
                            (sort) (last))
                       (str (t/date)))
        type          (attr :Typ article)
        status        (attr :Status article)
        source        (attr :Quelle article)
        author        (attr :Autor article)
        editor        (attr :Redakteur article)
        timestamp     (attr :Zeitstempel article)
        tranche       (attr :Tranche article)
        provenance    (attr :Erstfassung article)
        links         (->> (gx/elements :Verweis article)
                           (into [] (mapcat parse-link)))
        forms         (gx/elements :Formangabe article)
        forms         (concat (filter main-form? forms)
                              (remove main-form? forms))
        reprs         (mapcat (partial gx/elements :Schreibung) forms)
        repr-texts    (into [] (comp (mapcat texts) (distinct)) reprs)
        repr-hids     (into [] (comp (mapcat hids) (distinct)) reprs)
        grammars      (mapcat (partial gx/elements :Grammatik) forms)
        glosses       (->> (gx/elements :Definition article)
                           (into [] (comp (mapcat texts) (distinct))))
        pos           (->> grammars
                           (mapcat (partial gx/elements :Wortklasse))
                           (mapcat texts)
                           (first))
        gender        (->> grammars
                           (mapcat (partial gx/elements :Genus))
                           (mapcat texts)
                           (first))]
    (cond-> {:last-modified last-modified}
      type             (assoc :type type)
      status           (assoc :status status)
      source           (assoc :source source)
      author           (assoc :author author)
      editor           (assoc :editor editor)
      timestamp        (assoc :timestamp timestamp)
      tranche          (assoc :tranche tranche)
      provenance       (assoc :provenance provenance)
      pos              (assoc :pos pos)
      gender           (assoc :gender gender)
      (seq repr-texts) (assoc :forms repr-texts)
      (seq repr-hids)  (assoc :anchors repr-hids)
      (seq links)      (assoc :links links)
      (seq glosses)    (assoc :definitions glosses))))

(defn metadata
  [node]
  (first (map parse-article (gx/elements :Artikel node))))

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

(def xml-template
  (read-xml (io/resource "zdl/lex/article/template.xml")))

(defn new-article-xml
  [xml-id form pos author]
  (->str
   (postwalk
    (fn [node]
      (if (map? node)
        (condp = (node :tag)
          :Artikel    (let [ts (str (t/date))]
                        (update node :attrs merge
                                {:xml:id           xml-id
                                 :Zeitstempel      ts
                                 :Erstellungsdatum ts
                                 :Autor            author}))
          :Schreibung (assoc node :content (list form))
          :Wortklasse (assoc node :content (list pos))
          node)
        node))
    xml-template)))

(defn form->filename
  [form]
  (-> form
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"\p{InCombiningDiacriticalMarks}" "")
      (str/replace "ß" "ss")
      (str/replace " " "_")
      (str/replace #"[^\p{Alpha}\p{Digit}\-_]" "_")))
