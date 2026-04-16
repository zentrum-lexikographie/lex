(ns zdl.lex.fixtures
  (:require
   [babashka.fs :as fs]
   [clojure.java.process :as p]
   [clojure.test :refer [join-fixtures]]
   [com.potetm.fusebox.retry :as retry :refer [with-retry delay-exp]]
   [integrant.core :as ig]
   [next.jdbc :as jdbc]
   [zdl.lex.server :as server]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.git :as git]
   [taoensso.telemere :as tm]))

(defn start-backends!
  []
  (p/exec "docker" "compose" "--progress" "quiet" "up" "-d" "db" "index" "queue"))

(def backend-retry
  (retry/init {::retry/retry? (fn [n _ms _ex] (< n 20))
               ::retry/delay  (fn [n _ms _ex] (min (delay-exp 100 n) 1000))}))

(defn wait-for-backends!
  []
  (tm/with-min-level nil "com.potetm.fusebox.retry" :warn
    (with-retry backend-retry
      (index/query {"q" "id:*" "rows" "0" "wt" "json"})
      (jdbc/execute! (server/config :zdl.lex.server.db/connection) ["SELECT 1+1 AS n"]))))

(defn stop-backends!
  []
  (p/exec "docker" "compose"  "--progress" "quiet" "down" "db" "index" "queue"))

(defn backends
  [f]
  (start-backends!) (try (wait-for-backends!) (f) (finally (stop-backends!))))

(def config
  {::test-data {:repo (ig/ref ::git/repository)}})

(defmethod ig/init-key ::test-data
  [_ {{git-dir :dir :as repo} :repo}]
  (run! #(git/rm! repo %) (map :id (git/article-descs git-dir)))
  (git/commit! repo)
  (fs/copy-tree (fs/file "test" "data") git-dir)
  (run! #(git/add! repo %) (map :id (git/article-descs git-dir)))
  (git/commit! repo))

(def ^:dynamic *system*
  nil)

(defn system
  [f]
  (let [system (ig/init (merge server/config-without-tasks config))]
    (try
      (binding [*system* system]
        (f))
      (finally
        (ig/halt! system)))))

(def all
  (join-fixtures [backends system]))
