(ns zdl.lex.fixtures
  (:require
   [babashka.fs :as fs]
   [clojure.java.process :as p]
   [clojure.test :refer [join-fixtures]]
   [com.potetm.fusebox.retry :as retry :refer [with-retry delay-exp]]
   [integrant.core :as ig]
   [pg.core :as pg]
   [zdl.lex.server :as server]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.git :as git]
   [taoensso.telemere :as tm]))

(def backend-retry
  (retry/init {::retry/retry? (fn [n _ms _ex] (< n 20))
               ::retry/delay  (fn [n _ms _ex] (min (delay-exp 100 n) 1000))}))

(defn wait-for-backends!
  []
  (tm/with-min-level nil "com.potetm.fusebox.retry" :warn
    (with-retry backend-retry
      (index/query {"q" "id:*" "rows" "0" "wt" "json"})
      (pg/execute (server/config :zdl.lex.server.db/connection) "SELECT 1+1 AS n"))))

(defn start-backends!
  []
  (p/exec "docker" "compose" "--progress" "quiet" "up" "-d" "db" "index"))

(defn stop-backends!
  []
  (p/exec "docker" "compose"  "--progress" "quiet" "down" "db" "index"))

(defn backends
  [f]
  (start-backends!) (try (wait-for-backends!) (f) (finally (stop-backends!))))

(def ^:dynamic *system*
  nil)

(def config
  (dissoc server/config
          :zdl.lex.metrics/reporter
          :zdl.lex.server.schedule/tasks))

(defn system
  [f]
  (let [system (ig/init config)]
    (try
      (binding [*system* system]
        (f))
      (finally
        (ig/halt! system)))))

(defn init-with-test-data!
  []
  (let [{{git-dir :dir :as repo} ::git/repository} *system*]
    (when (= 1 (count (fs/list-dir git-dir))) ; empty repo, only '.git/'
      (index/clear! "article")
      (fs/copy-tree (fs/file "test" "data") git-dir)
      (run! #(git/add! repo %) (map :id (git/article-descs git-dir)))
      (git/commit! repo))))

(defn test-data
  [f]
  (init-with-test-data!) (f))

(def all
  (join-fixtures [backends system test-data]))
