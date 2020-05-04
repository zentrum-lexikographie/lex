(ns zdl.lex.lucene-test
  (:require [zdl.lex.lucene :as lucene]
            [clojure.test :refer :all]))

(deftest translate
  (let [is= #(is (= (lucene/translate %1) %2))]
    (is= "quelle:a/b"
         "sources:a/b")
    (is= "-*:test OR +test2"
         "-*:test OR +test2")
    (is= "!test OR test3"
         "!test OR test3")
    (is= "!test OR [* TO 2019-01-01]"
         "!test OR [* TO 2019-01-01T00\\:00\\:00Z]")
    (is= "/te\\/st/"
         "/te\\/st/")
    (is= "\"test \\\" 1\"~2"
         "\"test \\\" 1\"~2")
    (is= "autor:me^10 OR def:\"*gu\\*t*\""
         "author:me^10 OR definitions:\"*gu\\*t*\"")
    (is= "autor:test AND form:*te"
         "author:test AND forms:*te")
    (is= "status:(Red-f OR Red-2 OR Artikelrump*)"
         "status:(Red-f OR Red-2 OR Artikelrump*)")
    (is= "status:(Red-f OR Red-2 OR Artikelrump*) AND !autor:me"
         "status:(Red-f OR Red-2 OR Artikelrump*) AND !author:me")))
