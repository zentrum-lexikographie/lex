(ns zdl.lex.client-test
  (:require [zdl.lex.client :as client]
            [clojure.test :refer [deftest is use-fixtures]]
            [zdl.lex.fixtures :as fixtures]))

(use-fixtures :once fixtures/all)

(deftest query
  (is (some? (client/http-search "id:*"))))
