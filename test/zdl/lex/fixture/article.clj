(ns zdl.lex.fixture.article
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as gen]
            [gremid.data.xml.rngom :as dx.rngom]
            [zdl.lex.article.validate :as article.validate]))

(def model
  (delay
    (->> article.validate/rng-schema-source
         (dx.rngom/parse-schema) (dx.rngom/traverse))))

(def article-qn
  "{http://www.dwds.de/ns/1.0}Artikel")

(defn article-attr-vals
  [k]
  (into #{} (dx.rngom/attribute-values article-qn k @model)))

(def gen-article-attr-val
  (comp gen/elements article-attr-vals))

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
