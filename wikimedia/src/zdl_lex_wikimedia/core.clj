(ns zdl-lex-wikimedia.core
  (:gen-class)
  (:require [clojure.data.zip :as dz]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [zdl-lex-wikimedia.dump :as dump]
            [zdl-lex-wikimedia.util :refer [->clean-map]]
            [zdl-lex-wikimedia.wikitext :as wt])
  (:import [org.sweble.wikitext.parser.nodes
            WtDefinitionList WtDefinitionListDef
            WtExternalLink WtInternalLink
            WtName
            WtTemplate WtTemplateArgument WtTemplateArguments
            WtValue]))

(def >> dz/descendants)

(def text (comp not-empty str/trim wt/loc->text))

(defn template-args [loc]
  (into (sorted-map)
        (for [arg (wt/nodes-> loc WtTemplateArguments WtTemplateArgument)
              :let [k (wt/node-> arg WtName text)
                    v (wt/node-> arg WtValue text)]]
          (if (and k v) [k v]))))

(defn template-values [loc]
  "Extracts the argument values of a template node"
  (wt/nodes-> loc WtTemplateArguments WtTemplateArgument WtValue text))

(defn link-node? [loc]
  (let [node (zip/node loc)]
    (cond (instance? WtInternalLink node) true
          (instance? WtExternalLink node) true
          :else false)))

(defn links [loc]
  (seq (wt/nodes-> loc >> link-node? zip/node #(.getTarget %) wt/text)))

(defn parse [{:keys [title content] :as page}]
  "Parses a Wiktionary page, extracting lexicographic data"
  (let [ast (wt/parse content)
        loc (wt/zipper ast)

        ;; templates by name
        template-locs (wt/nodes-> loc >> WtTemplate)
        template-locs (group-by #(wt/node-> % WtName text) template-locs)

        template-data #(some-> (template-locs %) (first) (template-values) (seq))

        ;; definition lists
        dl-locs (wt/nodes-> loc >> WtDefinitionList WtDefinitionListDef)

        ;; definition lists, grouped by named template in preceding node
        dl-locs (group-by #(wt/node-> % zip/up dz/left-locs
                                      WtTemplate WtName text)
                          dl-locs)

        dl-texts #(some->> (dl-locs %) (map text) seq)
        dl-links #(some->> (dl-locs %) (map links) flatten seq)

        type (template-data "Wortart")
        ;; TODO: parse and filter
        infobox (some->
                 (wt/node-> loc >> WtTemplate
                            [WtName text #(re-matches #"Deutsch.*?Übersicht\s*$" %)])
                 (template-args))
        translations (->> (concat (template-locs "Üt")
                                  (template-locs "Ü"))
                          (map template-values)
                          (filter (comp (partial < 1) count))
                          (seq))]
    (->> page
         (merge {:ast ast
                 :pos (first type)
                 :lang (second type)
                 :dwds (template-data "Ref-DWDS")
                 :hyphenation (dl-texts "Worttrennung")
                 :pronounciation (template-data "Lautschrift")
                 :senses (concat (dl-texts "Bedeutungen")
                                 (dl-texts "Bedeutungen"))
                 :examples (dl-texts "Beispiele")
                 :collocations (dl-links "Charakteristische Wortkombinationen")
                 :synonyms (dl-links "Synonyme")
                 :hyperonyms (dl-links "Oberbegriffe")
                 :hyponyms (dl-links "Unterbegriffe")
                 :antonyms (concat (dl-links "Gegenworte")
                                   (dl-links "Gegenwörter"))
                 :etym-related (dl-links "Sinnverwandte Wörter")
                 :derived (dl-links "Abgeleitete Begriffe")
                 :etymology (dl-texts "Herkunft")
                 :references (dl-texts "Referenzen")
                 :infobox infobox
                 :translations translations})
         (->clean-map))))

(def wiktionary-de (io/file "data/dewiktionary.xml"))

(comment
  (time
   (->> (dump/pages wiktionary-de)
        (filter (comp (partial = "Sinn") :title))
        (map parse)
        ;;(filter (comp (partial = "Deutsch") :lang))
        ;;(map :ast)
        (map #(dissoc % :content))
        ;;(filter #(= "Verb" (:pos %)))
        first)))

(defn -main [& dumps]
  (doseq [dump dumps]
    (->> (io/file dump)
         (dump/pages)
         (map parse)
         (map #(dissoc % :content :ast))
         (map println)
         (doall))))
