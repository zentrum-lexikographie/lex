(ns zdl.lex.fixtures
  (:require
   [babashka.fs :as fs]
   [clojure.java.process :as p]
   [clojure.test :refer [join-fixtures]]
   [com.potetm.fusebox.retry :as retry :refer [with-retry delay-exp]]
   [zdl.lex.env :as env]
   [zdl.lex.server :as server]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.git :as git]
   [next.jdbc :as jdbc]))

(def backend-retry
  (retry/init {::retry/retry? (fn [n _ms _ex] (< n 20))
               ::retry/delay  (fn [n _ms _ex] (min (delay-exp 100 n) 1000))}))

(defn wait-for-backends!
  []
  (with-retry backend-retry
    (index/query {"q" "id:*" "rows" "0" "wt" "json"})
    (jdbc/execute! env/db ["SELECT 1+1 AS n"])))

(defn start-backends!
  []
  (p/exec "docker" "compose" "--progress" "quiet" "up" "db" "index" "queue" "-d"))

(defn stop-backends!
  []
  (p/exec "docker" "compose"  "--progress" "quiet" "down" "db" "index" "queue"))

(defn backends
  [f]
  (start-backends!) (try (wait-for-backends!) (f) (finally (stop-backends!))))

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
  (join-fixtures [backends test-data server]))
