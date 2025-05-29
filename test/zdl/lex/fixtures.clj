(ns zdl.lex.fixtures
  (:require
   [babashka.fs :as fs]
   [clojure.java.process :as p]
   [clojure.test :refer [join-fixtures]]
   [diehard.core :as dh]
   [zdl.lex.env :as env]
   [zdl.lex.server :as server]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.git :as git]))

(defn wait-for-solr!
  []
  (dh/with-retry {:backoff-ms  [500 1000 1.1]
                  :max-retries 20}
    (some? (index/query {"q" "id:*" "rows" "0" "wt" "json"}))))

(defn start-solr!
  []
  (p/exec "docker" "compose" "up" "index" "-d"))

(defn stop-solr!
  []
  (p/exec "docker" "compose" "down" "index"))

(defn solr
  [f]
  (start-solr!) (try (wait-for-solr!) (f) (finally (stop-solr!))))

(defn init-with-test-data!
  []
  (index/clear! "article")
  (fs/delete-tree env/git-dir)
  (git/init!)
  (fs/copy-tree (fs/file "test" "data") env/git-dir)
  (run! git/add! (map :id (git/article-descs)))
  (git/commit!))

(defn test-data
  [f]
  (init-with-test-data!) (f))

(defn server
  [f]
  (try
    (server/start)
    (f)
    (finally
      (server/stop))))


(def all
  (join-fixtures [solr test-data server]))
