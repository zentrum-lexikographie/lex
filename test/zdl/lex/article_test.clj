(ns zdl.lex.article-test
  (:require [clojure.test :refer [deftest is]]
            [zdl.lex.article :as article]
            [zdl.lex.article.validate :as av]
            [zdl.lex.article.xml :as axml]
            [zdl.lex.server.git :as git]))

(deftest map-cleaning
  (is (= (article/clean-map {:a nil :b :c}) {:b :c})))

(deftest typography-check
  (is
   (or (some->> (article/files git/dir) (random-sample 0.01) (first)
                (axml/read-xml)
                (av/check-typography))
       true)))
