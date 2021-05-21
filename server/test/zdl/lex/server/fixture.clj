(ns zdl.lex.server.fixture
  (:require [clojure.test :refer :all]
            [zdl.lex.server :as server]
            [zdl.lex.server.gen.article :refer [article-set-fixture]]
            [zdl.lex.server.article :as article]))

(defn server-fixture
  [f]
  (try (server/start) (f) (finally (server/stop))))

(defn index-fixture
  [f]
  @(article/refresh-articles!)
  (f))

(def backend-fixture
  (join-fixtures [article-set-fixture server-fixture index-fixture]))
