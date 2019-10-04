(ns zdl-lex-wikimedia.article
  (:refer-clojure :exclude [descendants])
  (:require [clojure.data.zip :refer [descendants right-locs children]]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [clj-excel.core :as xls]
            [zdl-lex-wikimedia.dump :as dump]
            [zdl-lex-common.util :refer [->clean-map]]
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
     {:pos-set (apply sorted-set pos)
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
     :lang (wt/node-> heading WtTemplate [WtName "Sprache"] template-values)
     :types (wt/nodes-> loc WtBody WtSection (section-level 3) parse-types)}))

(defn parse [{:keys [content] :as page}]
  (let [loc (-> content wt/parse wt/zipper)]
    (assoc page :entries (wt/nodes-> loc WtSection (section-level 2) parse-entry))))
