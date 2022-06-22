(ns zdl.lex.article-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [zdl.lex.fixture.git :as fixture.git]
   [zdl.lex.fixture.system :as fixture.system]
   [zdl.lex.server.git :as server.git]))

(use-fixtures :once fixture.git/articles)

(deftest typography-check
  (is (last (server.git/articles fixture.system/git-dir))))
