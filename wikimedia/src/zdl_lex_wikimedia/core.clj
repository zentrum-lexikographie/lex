(ns zdl-lex-wikimedia.core
  (:gen-class)
  (:refer-clojure :exclude [descendants])
  (:require [clojure.data.zip :refer [descendants right-locs children]]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [clj-excel.core :as xls]
            [zdl-lex-wikimedia.dump :as dump]
            [zdl-lex-common.util :refer [->clean-map]]
            [zdl-lex-wikimedia.part-of-speech :as pos]
            [zdl-lex-wikimedia.wikitext :as wt]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.article :as article])
  (:import org.apache.jena.riot.RDFFormat
           [org.apache.jena.riot.system StreamOps StreamRDFWriter]
           [org.sweble.wikitext.parser.nodes WtBody WtDefinitionList WtDefinitionListDef WtHeading WtInternalLink WtName WtSection WtTemplate WtTemplateArgument WtTemplateArguments WtText WtValue]))

(defn text [loc]
  "The trimmed text content of a WikiText node"
  (-> loc wt/loc->text str/trim not-empty))

(defn template-values [loc]
  "Extracts the argument values of a template node"
  (wt/nodes-> loc WtTemplateArguments WtTemplateArgument WtValue text))

(defn section-level [level]
  "A predicate matching a section (node) of a certain level"
  (fn [loc] (= level (.getLevel ^WtSection (zip/node loc)))))

(defn definitions [templates name]
  (->>
   (mapcat
    #(take 1 (wt/nodes-> % zip/up right-locs WtDefinitionList))
    (templates name))
   (mapcat
    #(wt/nodes-> % WtDefinitionListDef))
   (seq)))

(defn remove-references [s]
  (not-empty (str/replace s #"^\[[^\]]+\]\s*" "")))

(defn ->data [loc]
  (-> {:text (some-> loc text remove-references not-empty)
       :links (seq (wt/nodes-> loc descendants WtInternalLink zip/node
                               #(.getTarget ^WtInternalLink %) wt/text))}
      (->clean-map)
      (not-empty)))

(defn parse-summary [templates]
  (when-let [summary (some->> templates
                              (filter (comp #(str/ends-with? % "Übersicht") first))
                              (first) (second)
                              (first))]
    (some->>
     (for [arg (wt/nodes-> summary WtTemplateArguments WtTemplateArgument)]
       (vector (wt/node-> arg WtName text) (wt/node-> arg WtValue text)))
     (seq)
     (into {}))))

(defn parse-types [loc]
  (let [pos (wt/nodes-> loc WtHeading WtTemplate [WtName "Wortart"] template-values)
        templates (->> (wt/nodes-> loc WtBody descendants WtTemplate)
                       (group-by #(or (wt/node-> % WtName text) "")))
        definitions (partial definitions templates)
        ->data (comp seq (partial map ->data) definitions)
        join (comp seq concat)]
    (->clean-map
     {:pos (apply sorted-set pos)
      :summary (parse-summary templates)
      :pronounciation (some->>
                       (definitions "Aussprache")
                       (filter #(wt/node-> % WtTemplate WtName "IPA"))
                       (mapcat #(wt/nodes-> % WtTemplate [WtName "Lautschrift"] text))
                       (seq))
      :hyphenation (->data "Worttrennung")
      :definitions (->data "Bedeutungen")
      :synonyms (->data "Synonyme")
      :examples (->data "Beispiele")
      :collocations (join (->data "Charakteristische Wortkombinationen")
                          (->data "Signifikante Kollokation"))
      :hyperonyms (->data "Oberbegriffe")
      :hyponyms (->data "Unterbegriffe")
      :antonyms (join (->data "Gegenworte")
                      (->data "Gegenwörter"))
      :etymology (->data "Herkunft")
      :etym-related (->data "Sinnverwandte Wörter")
      :derived (join (->data "Wortbildungen")
                     (->data "Abgeleitete Begriffe"))
      :references (some->>
                   (definitions "Referenzen")
                   (mapcat #(wt/nodes-> % WtTemplate WtName text))
                   (seq)
                   (apply sorted-set))
      :translations (->> (join (templates "Üt") (templates "Ü"))
                         (map template-values)
                         (filter (comp (partial < 1) count))
                         (seq))})))

(defn parse-entry [loc]
  (let [heading (wt/node-> loc WtHeading)
        title (text heading)]
    {:title title
     :head (-> title (str/replace #"\([^\)]*\)" "") (str/trim))
     :lang (wt/node-> heading WtTemplate [WtName "Sprache"] template-values)
     :types (wt/nodes-> loc WtBody WtSection (section-level 3) parse-types)}))

(defn parse [{:keys [content] :as page}]
  (let [loc (-> content wt/parse wt/zipper)]
    (assoc page :entries (wt/nodes-> loc WtSection (section-level 2) parse-entry))))

(defn parse-dump [pages]
  (for [page (pmap parse pages)
        :let [{:keys [title]} page]
        entry (:entries page)
        :when (= "Deutsch" (entry :lang))
        :let [{:keys [head types]} entry]
        type types
        :let [{:keys [pos summary references]} type
              pos (disj pos "Deutsch")
              genus (get summary "Genus")
              dwds-ref? (if references (some? (references "Ref-DWDS")) false)]]
    (->clean-map
     {:title title :form head
      :pos pos :genus genus
      :dwds-ref? dwds-ref?})))

(def wiktionary-de (io/file "data/dewiktionary.xml"))

(defn parse-wiktionary-de []
  (-> (io/file "data/dewiktionary.xml") dump/pages parse-dump))

(defn csv-index [csv-file]
  (with-open [toc (io/reader (io/file csv-file) :encoding "UTF-8")]
    (group-by first (csv/read-csv toc))))

(defn gap [zdl wkt]
  (if zdl
    (for [[head zdl-pos zdl-gender zdl-type zdl-source zdl-status] (second zdl)]
      [head zdl-pos zdl-gender zdl-type zdl-source zdl-status nil nil nil])
    (for [[head _ wkt-pos wkt-gender wkt-dwds?] (second wkt)]
      [head nil nil nil nil nil wkt-pos wkt-gender wkt-dwds?])))

(defn matched [zdl wkt]
  (let [[_ _ wkt-pos wkt-gender wkt-dwds?] (-> wkt second first)]
    (for [[head zdl-pos zdl-gender zdl-type zdl-source zdl-status] (second zdl)]
      [head zdl-pos zdl-gender zdl-type zdl-source zdl-status wkt-pos wkt-gender wkt-dwds?])))

(defn matches [zdl wkt]
  (let [next-zdl (first zdl)
        next-wkt (first wkt)
        zdl-key (first next-zdl)
        wkt-key (first next-wkt)
        cmp (if (and zdl-key wkt-key)
              (.. article/collator (compare zdl-key wkt-key)) 0)]
    (when-not (and (nil? next-zdl) (nil? next-wkt))
      (cond
        (or (nil? next-wkt) (< cmp 0))
        (concat (gap next-zdl nil)
                (lazy-seq (matches (rest zdl) wkt)))

        (or (nil? next-zdl) (> cmp 0))
        (concat (gap nil next-wkt)
                (lazy-seq (matches zdl (rest wkt))))

        :else
        (concat (matched next-zdl next-wkt)
                (lazy-seq (matches (rest zdl) (rest wkt))))))))

(def xls-header
  (map #(array-map :value % :bold true)
       ["Lemma"
        "DWDS-Wortklasse"
        "DWDS-Genus"
        "DWDS-Artikeltyp"
        "DWDS-Artikelquelle"
        "DWDS-Artikelstatus"
        "Wiktionary-Wortklasse"
        "Wiktionary-Genus"
        "Wiktionary-DWDS-Referenz"]))

(defn match->xls [match]
  (let [zdl? (some? (get match 5))
        wkt? (some? (get match 8))
        color (cond
                (and zdl? wkt?) :white
                zdl? :light-green
                wkt? :light-yellow)]
    (map #(array-map :value % :pattern :solid-foreground :foreground-color color)
         match)))

(comment
  (let [index (comp (partial sort-by first article/collator) seq csv-index)
        wkt (index "wkt-toc.csv")
        zdl (index "zdl-toc.csv")]
    (->
     (->> (matches zdl wkt)
          #_(drop 10000)
          #_(take 50000)
          (map match->xls)
          #_(concat [xls-header])
          #_(hash-map "DWDS - Wiktionary")
          #_(xls/build-workbook (xls/workbook-xssf))
          (map #(nth % 8))
          (map :value)
          (group-by identity)
          (map #(vector (first %) (-> % second count))))
     identity
     #_(xls/save (io/file "zdl-wkt.xlsx"))))

  (->> (csv-index "wkt-toc.csv") keys (sort article/collator) (count))
  (->> (csv-index "zdl-toc.csv") keys (sort article/collator) (count))

  (with-open [wkt-toc (io/writer (io/file "wkt-toc.csv") :encoding "UTF-8")]
    (->> (parse-wiktionary-de)
         (filter #(some pos/wkt (% :pos)))
         (map #(vector (% :title)
                       (or (% :form) "")
                       (str/join "|" (% :pos))
                       (or (% :genus) "")
                       (% :dwds-ref?)))
         (csv/write-csv wkt-toc)))

  (with-open [zdl-toc (io/writer (io/file "zdl-toc.csv") :encoding "UTF-8")]
    (->> (for [excerpt (article/excerpts "../data/git")
               :let [{:keys [forms pos type source status gender]} excerpt]
               form forms]
           [form
            (str/join "|" pos)
            (str/join "|" gender)
            type
            source
            status])
         (csv/write-csv zdl-toc)))

  (->> (parse-wiktionary-de)
       (filter #(some pos/wkt (% :pos)))
       (count))) ;; => 108_875

(comment
  (defn wkt->trig []
    (with-open [rdf-stream (io/output-stream (io/file data-dir "wiktionary.trig"))]
      (let [rdf-stream (StreamRDFWriter/getWriterStream rdf-stream RDFFormat/TRIG_BLOCKS)
            ->rdf (fn [s p o] (.quad rdf-stream (wkt-triple s p o)))
            rdf-type (qn "rdf" "type")
            lexicon-entry (qn "lemon" "LexiconEntry")
            part-of-speech (qn "isocat" "partOfSpeech")
            canonical-form (qn "lemon" "canonicalForm")
            written-rep (qn "lemon" "writtenRep")
            phonetic-form (qn "isocat" "phoneticForm")
            sense (qn "lemon" "sense")
            definition (qn "lemon" "definition")
            value (qn "lemon" "value")]
        (.start rdf-stream)
        (StreamOps/sendPrefixesToStream prefixes rdf-stream)
        (doseq [entry (take 100 (drop (rand-int 1000) entries))
                :when (interesting-entry? entry)
                :let [page (.getPage entry)
                      word (.getWord entry)
                      s (wkt word)
                      word-form (wkt word "form")]]
          (->rdf s rdf-type lexicon-entry)
          (->rdf s part-of-speech (rdf-literal (pos entry)))
          (->rdf s canonical-form word-form)
          (->rdf word-form written-rep (rdf-literal word "de"))
          (doseq [pron (or (.getPronunciations entry) [])
                  :when (= "IPA" (.. pron (getType) (name)))]
            (when-let [pron (not-empty (.. pron (getText)))]
              (->rdf word-form phonetic-form (rdf-literal pron))))
          (doseq [entry-sense (.. entry (getSenses true))
                  :when (.. entry-sense (getGloss))
                  :let [id (.. entry-sense (getId))
                        text (.. entry-sense (getGloss) (getPlainText))
                        sense-node (wkt word (str "sense-" id))
                        definition-node (wkt word (str "sense-def-" id))]]
            (->rdf s sense sense-node)
            (->rdf sense-node definition definition-node)
            (->rdf definition-node value (rdf-literal text))))
        (.finish rdf-stream)))))

(defn- entry->pron [{:keys [head types]}]
  (for [type types]
    (merge {:head head} (select-keys type [:pos :summary :pronounciation]))))

(defn- pron->csv [{:keys [head pos pronounciation summary]}]
  (let [genus (some->> summary
                       (filter (fn [[k v]] (str/starts-with? (or k "") "Genus")))
                       (map second))]
    (concat [head (str/join " – " pos) (str/join "|" genus)] pronounciation)))

(comment
  (time (count (dump/pages wiktionary-de)))

  (->> (dump/pages wiktionary-de)
       ;;(filter (comp #{"Achtung"} :title))
       (pmap #(merge % (parse %)))
       (map #(dissoc % :content))
       (drop 1000)
       (take 10))

  (with-open [wkt-ipa (io/writer (io/file "wkt-ipa.csv") :encoding "UTF-8")]
    (->> (dump/pages wiktionary-de)
         ;;(filter (comp #{"Achtung"} :title))
         (pmap #(merge % (parse %)))
         (map #(dissoc % :content))
         (mapcat :entries)
         (mapcat entry->pron)
         (filter :pronounciation)
         (map pron->csv)
         #_(mapcat :entries)
         #_(mapcat :types)
         #_(mapcat (comp (partial take 1) :type))
         #_(into #{})
         #_(sort)
         ;;(filter (comp (partial some #{"Deutsch"}) (partial map :lang) :entries))
         ;;(filter (comp (partial < 1) count :entries))
         (csv/write-csv wkt-ipa))))

(defn -main [& dumps]
  (doseq [dump dumps]
    (->> (io/file dump)
         (dump/pages)
         (pmap parse)
         (map println)
         (dorun))))
