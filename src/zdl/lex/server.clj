(ns zdl.lex.server
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.data.csv :as csv]
   [integrant.core :as ig]
   [taoensso.telemere :as tm]
   [zdl.lex.env :as env :refer [getenv]]
   [zdl.lex.metrics :as metrics]
   [zdl.lex.server.db :as db]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.gpt :as gpt]
   [zdl.lex.server.http :as http]
   [zdl.lex.server.issue :as issue]
   [zdl.lex.server.schedule :as schedule]))

(def fallback-userbase
  {["admin" "admin"] {:user "admin" :password "admin" :description "Administrator"}})

(def userbase
  (let [userbase-file (fs/file (getenv "USERBASE_FILE" ".htauth.csv"))]
    (or
     (when (fs/readable? userbase-file)
       (with-open [r (io/reader userbase-file)]
         (let [[_header & users] (csv/read-csv r)]
           (into {}
                 (map (fn [[user password desc]]
                        [[user password] {:user        user
                                          :password    password
                                          :description desc}]))
                 users))))
     (do
       (tm/log! {:id ::userbase :level :warn :data fallback-userbase})
       fallback-userbase))))


(def config
  {::db/connection    {:dbtype   "mariadb"
                       :host     (getenv "DB_HOST" "localhost")
                       :port     (parse-long (getenv "DB_PORT" "3306"))
                       :dbname   (getenv "DB_NAME" "lex")
                       :user     (getenv "DB_USER" "lex")
                       :username (getenv "DB_USER" "lex")
                       :password (getenv "DB_PASSWORD" "lex")}
   ::git/repository   {:dir    (getenv "GIT_DIR" "wb")
                       :origin (getenv "GIT_ORIGIN")
                       :branch (getenv "GIT_BRANCH" "dev")}
   ::gpt/queue        {:host     (getenv "QUEUE_HOST" "localhost")
                       :port     (parse-long (getenv "QUEUE_PORT" "5671"))
                       :user     (getenv "QUEUE_USER" "lex")
                       :password (getenv "QUEUE_PASSWORD" "lex")
                       :queue    (getenv "QUEUE_GPT_QUEUE" "gpt")}
   ::http/server      {:port    (parse-long (getenv "HTTP_PORT" "3000"))
                       :db        (ig/ref ::db/connection)
                       :gpt-queue (ig/ref ::gpt/queue)
                       :issue-db  (ig/ref ::issue/connection)
                       :repo      (ig/ref ::git/repository)
                       :userbase  userbase}
   ::issue/connection {:dbtype   "mysql"
                       :host     (getenv "MANTIS_DB_HOST" "localhost")
                       :port     (parse-long (getenv "MANTIS_DB_PORT" "3306"))
                       :dbname   (getenv "MANTIS_DB_NAME" "mantis_bugtracker")
                       :username (getenv "MANTIS_DB_USER" "mantis")
                       :password (getenv "MANTIS_DB_PASSWORD" "mantis")}
   ::metrics/reporter {:interval (-> (getenv "METRICS_REPORTER_INTERVAL" "5")
                                     (parse-long))}
   ::schedule/tasks   {:db       (ig/ref ::db/connection)
                       :issue-db (ig/ref ::issue/connection)
                       :repo     (ig/ref ::git/repository)}})

(def config-without-tasks
  (dissoc config :zdl.lex.metrics/reporter :zdl.lex.server.schedule/tasks))

(defn -main
  [& _]
  (let [system (ig/init config)]
    (. (Runtime/getRuntime) (addShutdownHook (Thread. #(ig/halt! system))))
    @(promise)))
