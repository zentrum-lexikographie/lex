(ns zdl.lex.server.gen.schema
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer :all]
            [zdl.lex.fs :refer [file]]
            [zdl.xml.rngom :as rngom]))

(def model
  (delay
    (->> (file "../oxygen/framework/rng/DWDSWB.rng")
         (rngom/parse-schema) (rngom/traverse))))

(def article-qn
  "{http://www.dwds.de/ns/1.0}Artikel")

(defn article-attr-vals
  [k]
  (into #{} (rngom/attribute-values article-qn k @model)))

(def gen-article-attr-val
  (comp s/gen article-attr-vals))

(defn gen-author
  []
  (gen/such-that (complement #{"DWDS"}) (gen-article-attr-val "Autor")))

(defn gen-source
  []
  (gen-article-attr-val "Quelle"))

(defn gen-status
  []
  (gen-article-attr-val "Status"))

(defn gen-type
  []
  (gen-article-attr-val "Typ"))

(deftest generators
  (is [(gen/generate (gen-author))
       (gen/generate (gen-source))
       (gen/generate (gen-status))
       (gen/generate (gen-type))]))
