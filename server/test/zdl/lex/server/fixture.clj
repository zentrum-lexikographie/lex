(ns zdl.lex.server.fixture
  (:require [clojure.test :refer :all]
            [zdl.lex.server :as server]
            [zdl.lex.server.gen.article :refer [article-set-fixture]]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.solr.client :as solr-client]))

(defn server-fixture
  [f]
  (try (server/start) (f) (finally (server/stop))))

(defn index-fixture
  [f]
  @(solr-client/clear-index)
  @(article/refresh-articles!)
  (f))

(def backend-fixture
  (join-fixtures [article-set-fixture server-fixture index-fixture]))
