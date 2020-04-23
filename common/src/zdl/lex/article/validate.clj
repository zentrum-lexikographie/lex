(ns zdl.lex.article.validate
  (:require [clojure.java.io :as io]
            [zdl.lex.article.chars :as chars]
            [zdl.lex.article.token :as token]
            [zdl.lex.util :refer [->clean-map file]]
            [zdl-xml.util :as xml]
            [zdl-xml.validate :as xv]))

(def rng-validate
  (xv/create-rng-validator (io/resource "rng/DWDSWB.rng")))

(def sch-validate
  (xv/create-sch-validator (io/resource "rng/DWDSWB.sch.xsl")))

(def typography-checks
  (concat chars/checks token/checks))

(defn check-typography
  [article]
  (for [[select-ctx check type] typography-checks
        ctx (select-ctx article)
        :let [data (some->> ctx xml/->str xml/text check)]
        :when data]
    {:type type :ctx ctx :data data}))

(defn check
  [file article]
  (let [typography? (seq (check-typography article))
        rng? (seq (rng-validate file))
        sch? (seq (sch-validate file))]
    (->clean-map
     {:errors (seq (concat (when typography? ["Typographie"])
                           (when rng? ["Schema"])
                           (when sch? ["Schematron"])))})))
