(ns zdl-lex-wikimedia.core
  (:refer-clojure :exclude [descendants])
  (:require [clojure.data.zip :refer [descendants right-locs children]]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [clj-excel.core :as xls]
            [zdl-lex-wikimedia.wiktionary :as wkt]
            [zdl-lex-wikimedia.wiktionary.article :as wkt-article]
            [zdl-lex-wikimedia.dump :as dump]
            [zdl-lex-common.util :refer [->clean-map]]
            [zdl-lex-wikimedia.wiktionary :as wkt]
            [zdl-lex-wikimedia.wikitext :as wt]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.article :as article])
  (:import org.apache.jena.riot.RDFFormat
           [org.apache.jena.riot.system StreamOps StreamRDFWriter]
           [org.sweble.wikitext.parser.nodes WtBody WtDefinitionList WtDefinitionListDef WtHeading WtInternalLink WtName WtSection WtTemplate WtTemplateArgument WtTemplateArguments WtText WtValue]))

(defn parse-dump [pages]
  (for [page (pmap wkt-article/parse pages)
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
  (-> (io/file "data/dewiktionary.xml") #_dump/revisions parse-dump))

(comment
  (->> (io/file "data/dewiktionary.xml")
       (dump/pages)
       (pmap wkt-article/parse)
       (wkt-article/german-entries)
       (wkt-article/german-base-forms)
       (filter (comp (partial < 1) count :definitions))
       #_(map #(dissoc % :content))
       (map #(select-keys % [:title :pos-set :collocations :examples :definitions]))
       #_(drop 1000)
       (take 2)))

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
         (filter #(some wkt-article/base-form-pos (% :pos)))
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
       (filter #(some wkt-article/base-form-pos (% :pos)))
       (count))) ;; => 108_875
