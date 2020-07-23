(ns zdl.lex.server.fixture
  (:require  [clojure.test :refer :all]
             [zdl.lex.server :as server]
             [zdl.lex.client :as client]
             [zdl.lex.server.gen.article :refer [create-article-set-fixture]]
             [zdl.lex.server.solr.client :as solr-client]
             [clojure.tools.logging :as log]
             [clj-http.client :as http]))

(defn server-fixture
  [f]
  (try (server/start) (f) (finally (server/stop))))

(defn index-fixture
  [f]
  (try (solr-client/rebuild-index) (f) (finally (solr-client/clear-index))))

(defn backend-fixture
  ([]
   (backend-fixture [100 200]))
  ([article-range]
   (join-fixtures
    [(create-article-set-fixture article-range) server-fixture index-fixture])))

(use-fixtures :once (backend-fixture))

(deftest fixtures
  (is (log/spy (http/request (client/search-articles "id:*" 1)))))
