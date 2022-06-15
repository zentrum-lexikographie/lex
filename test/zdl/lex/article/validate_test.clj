(ns zdl.lex.article.validate-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [zdl.lex.article :as article]
            [zdl.lex.article.validate :as av]
            [zdl.lex.fixture.git :as fixture.git]
            [zdl.lex.server.git :as server.git]))

(use-fixtures :once fixture.git/articles)

(deftest typography-check
  (is
   (coll? (->> (article/files server.git/dir)
               (map article/read-xml)
               (map av/check-typography)
               (doall)))))
