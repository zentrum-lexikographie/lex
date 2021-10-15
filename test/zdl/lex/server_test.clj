(ns zdl.lex.server-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [zdl.lex.client :as client]
            [zdl.lex.server.fixture :refer [backend-fixture]]))

(use-fixtures :once backend-fixture)

(deftest search
  (is (log/spy :info @(client/search-articles "id:*" :limit 1))))
