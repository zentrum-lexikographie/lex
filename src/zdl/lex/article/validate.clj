(ns zdl.lex.article.validate
  (:require [clojure.data.xml :as dx]
            [clojure.java.io :as io]
            [zdl.lex.article.chars :as chars]
            [zdl.lex.article.token :as tokens]
            [zdl.lex.article.xml :as axml]
            [zdl.lex.fs :refer [file]]
            [zdl.xml.validate :as xv]))

(def rng-validate
  (xv/create-rng-validator (io/resource "framework/rng/DWDSWB.rng")))

(def sch-validate
  (xv/create-sch-validator (io/resource "framework/rng/DWDSWB.sch.xsl")))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")

(def checks-by-element
  {::dwds/Belegtext         [chars/check-text
                             chars/check-parentheses
                             tokens/check-ends-with-punctuation
                             tokens/check-missing-whitespace
                             tokens/check-redundant-whitespace]
   ::dwds/Definition        [chars/check-phrase
                             chars/check-parentheses
                             tokens/check-unknown-abbreviations
                             tokens/check-missing-whitespace
                             tokens/check-redundant-whitespace]
   ::dwds/Fundstelle        [chars/check-all]
   ::dwds/Kollokation       [chars/check-all]
   ::dwds/Formangabe        [chars/check-grammar]
   ::dwds/Paraphrase        [chars/check-phrase]
   ::dwds/Kompetenzbeispiel [chars/check-parentheses]})

(defn check-node
  [node check-fns]
  (when (seq check-fns)
    (when-let [s (axml/text node)]
      (map #(assoc % :ctx node) (remove nil? (map #(% s) check-fns))))))

(defn check-typography
  [node]
  (when (map? node)
    (let [{:keys [tag content]} node]
      (concat
       (when-let [checks (checks-by-element tag)]
         (check-node node checks))
       (mapcat check-typography content)))))
