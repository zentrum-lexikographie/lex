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
  {::db/connection    {:host            (getenv "DB_HOST" "db")
                       :port            (parse-long (getenv "DB_PORT" "5432"))
                       :database        (getenv "DB_NAME" "lex")
                       :user            (getenv "DB_USER" "lex")
                       :password        (getenv "DB_PASSWORD" "lex")
                       :ssl?            true
                       :migrations-path "zdl/lex/server/db"
                       :pool-max-size   8}
   ::git/repository   {:dir    (getenv "GIT_DIR" "/data/git")
                       :origin (getenv "GIT_ORIGIN" "git@git.zdl.org:zdl/dict.git")
                       :branch (getenv "GIT_BRANCH" "dev")}
   ::gpt/queue        {:host     (getenv "QUEUE_HOST" "labor.dwds.de")
                       :port     (parse-long (getenv "QUEUE_PORT" "5671"))
                       :user     (getenv "QUEUE_USER" "lex")
                       :password (getenv "QUEUE_PASSWORD" "lex")
                       :queue    (getenv "QUEUE_GPT_QUEUE" "gpt")}
   ::http/handler     {:db        (ig/ref ::db/connection)
                       :gpt-queue (ig/ref ::gpt/queue)
                       :issue-db  (ig/ref ::issue/connection)
                       :repo      (ig/ref ::git/repository)
                       :userbase  userbase}
   ::http/server      {:handler (ig/ref ::http/handler)
                       :port    (parse-long (getenv "HTTP_PORT" "3000"))}
   ::issue/connection {:db {:dbtype   "mysql"
                            :host     (getenv "MANTIS_DB_HOST" "mantis.dwds.de")
                            :port     (parse-long (getenv "MANTIS_DB_PORT" "3306"))
                            :dbname   (getenv "MANTIS_DB_NAME" "mantis_bugtracker")
                            :username (getenv "MANTIS_DB_USER" "mantis")
                            :password (getenv "MANTIS_DB_PASSWORD" "mantis")}}
   ::metrics/reporter {:interval (-> (getenv "METRICS_REPORTER_INTERVAL" "5")
                                     (parse-long))}
   ::schedule/tasks   {:db       (ig/ref ::db/connection)
                       :issue-db (ig/ref ::issue/connection)
                       :repo     (ig/ref ::git/repository)}})

(defn -main
  [& _]
  (let [system (ig/init config)]
    (. (Runtime/getRuntime) (addShutdownHook (Thread. #(ig/halt! system))))
    @(promise)))
