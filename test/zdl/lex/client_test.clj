(ns zdl.lex.client-test
  (:require
   [clojure.test :refer [deftest is]]
   [integrant.core :as ig]
   [zdl.lex.client :as client]
   [zdl.lex.dev :as dev]
   [zdl.lex.server :as server]))

(deftest query
  (let [system (ig/init (dev/assoc-backend-config server/config))]
    (try
      (is (some? (client/http-search "id:*")))
      (finally
        (ig/halt! system)))))
