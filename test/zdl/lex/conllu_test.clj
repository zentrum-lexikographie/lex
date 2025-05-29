(ns zdl.lex.conllu-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [zdl.lex.server.conllu :as conllu]))

(deftest parse-sample
  (with-open [r (io/reader (io/resource "zdl/lex/sample.conll"))]
    (is (every? map? (conllu/parse (line-seq r))))))
