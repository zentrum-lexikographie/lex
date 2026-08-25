(ns zdl.lex.client-test
  (:require
   [clojure.test :refer [deftest is join-fixtures use-fixtures]]
   [integrant.core :as ig]
   [zdl.lex.client :as client]
   [zdl.lex.dev :as dev]
   [zdl.lex.server :as server]))

(use-fixtures :once (join-fixtures [dev/backend-fixture
                                    dev/test-data-fixture
                                    dev/index-fixture]))

(deftest query
  (let [system (ig/init server/config)]
    (try
      (is (some? (client/http-search "id:*")))
      (finally
        (ig/halt! system)))))
