(ns zdl.lex.server.fixture
  (:require [clojure.test :refer :all]
            [zdl.lex.article :as article]
            [zdl.lex.server :as server]
            [zdl.lex.server.gen.article :refer [article-set-fixture]]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.solr.client :as solr-client]))

(defn server-fixture
  [f]
  (try (server/start) (f) (finally (server/stop))))

(defn index-fixture
  [f]
  (try
    @(solr-client/rebuild-index
      (mapcat article/extract-articles (article/article-files git/dir)))
    (f)
    (finally
      @(solr-client/clear-index))))

(def backend-fixture
  (join-fixtures [article-set-fixture server-fixture index-fixture]))
