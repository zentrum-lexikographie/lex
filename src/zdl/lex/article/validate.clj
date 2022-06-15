(ns zdl.lex.article.validate
  (:require [gremid.data.xml :as dx]
            [gremid.data.xml.rng :as dx.rng]
            [gremid.data.xml.schematron :as dx.schematron]
            [clojure.java.io :as io]
            [zdl.lex.article :as article]
            [zdl.lex.article.chars :as chars]
            [zdl.lex.article.token :as tokens]))

(def rng-schema-source
  (io/resource "framework/rng/DWDSWB.rng"))

(def rng-schema
  (dx.rng/->schema rng-schema-source))

(defn rng-validate
  [source]
  (dx.rng/validate rng-schema source))

(def sch-validate
  (dx.schematron/validator
   (io/resource "framework/rng/DWDSWB.sch.xsl")))

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
    (when-let [s (article/text node)]
      (map #(assoc % :ctx node) (remove nil? (map #(% s) check-fns))))))

(defn check-typography
  [node]
  (when (map? node)
    (let [{:keys [tag content]} node]
      (concat
       (when-let [checks (checks-by-element tag)]
         (check-node node checks))
       (mapcat check-typography content)))))

(defn check-for-errors
  [xml file]
  {:errors
   (cond-> []
     (seq (check-typography xml)) (conj "Typographie")
     (seq (rng-validate file))    (conj "Schema")
     (seq (sch-validate file))    (conj "Schematron"))})

(comment
  (mapcat sch-validate (filter #(.. % (getName) (endsWith ".xml")) (file-seq (io/file "data" "git"))))
  (rng-validate (io/file "data" "git" "DWDS" "000-Adjektive" "aussereuropaeisch.xml"))
  (sch-validate (io/file "data" "git" "DWDS" "000-Adjektive" "aussereuropaeisch.xml")))
