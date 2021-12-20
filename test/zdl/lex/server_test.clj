(ns zdl.lex.server-test
  (:require [clojure.test :refer [deftest is use-fixtures join-fixtures]]
            [zdl.lex.fixture.server :as fixture.server]
            [zdl.lex.fixture.solr :as fixture.solr]
            [zdl.lex.fixture.git :as fixture.git]
            [clojure.tools.logging :as log]
            [zdl.lex.client.http :as client.http]))

(use-fixtures :once
  (join-fixtures [fixture.git/articles fixture.solr/articles fixture.server/instance]))

(deftest placeholder
  (is
   (log/spy
    :info
    (client.http/request
     {:url "index" :query-params {:q "quelle:ZDL"}}))))
