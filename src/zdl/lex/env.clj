(ns zdl.lex.env
  (:require
   [babashka.fs :as fs]
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [lambdaisland.uri :as uri]
   [taoensso.telemere :as t])
  (:import
   (io.github.cdimascio.dotenv Dotenv)))

(t/uncaught->error!)
(t/set-min-level! nil 
                  [["com.zaxxer(.*)" :warn]
                   ["*" :info]])

(defn read-dot-env
  [filename]
  (.. Dotenv (configure) (filename filename) (ignoreIfMissing) (load)))

(def ^Dotenv dot-env
  (read-dot-env ".env"))

(def ^Dotenv dot-env-dev
  (read-dot-env ".env.dev"))

(defn getenv
  ([k]
   (getenv k nil))
  ([k df]
   (let [k (str "ZDL_LEX_" (csk/->SCREAMING_SNAKE_CASE_STRING k))]
     (some-> (or (System/getenv k)
                 (.get dot-env k)
                 (.get dot-env-dev k)
                 df)
             str/trim not-empty))))

(def server-url
  (getenv "SERVER_URL" "https://lex.dwds.de"))

(def server-user
  (getenv "SERVER_USER"))

(def server-password
  (getenv "SERVER_PASSWORD"))

(def server-auth
  (when (and server-user server-password) [server-user server-password]))

(def repl-port
  (Long/parseLong (getenv "REPL_PORT" "3001")))

(def git-origin
  (getenv "GIT_ORIGIN" "git@git.zdl.org:zdl/wb.git"))

(def git-branch
  (getenv "GIT_BRANCH" "zdl-lex-server/production"))

(def git-dir
  (getenv "GIT_DIR" "/data/git"))

(def http-port
  (Long/parseLong (getenv "HTTP_PORT" "3000")))

(def solr-url
  (uri/join (getenv "SOLR_URL" "http://index:8983/solr/")
            (str (getenv "SOLR_CORE" "articles") "/")))

(def lock-db
  (let [lock-db-path (fs/absolutize (getenv "LOCK_DB_PATH" "/data/locks"))]
    {:jdbcUrl       (-> {:scheme "jdbc:h2" :path lock-db-path} (uri/map->URI) (str))
     :username      "sa"
     :password      ""
     ::lock-db-path lock-db-path}))

(def mantis-db
  {:dbtype   "mysql"
   :host     (getenv "MANTIS_DB_HOST" "mantis.dwds.de")
   :port     (Long/parseLong (getenv "MANTIS_DB_PORT" "3306"))
   :dbname   (getenv "MANTIS_DB_NAME" "mantis_bugtracker")
   :username (getenv "MANTIS_DB_USER" "mantis")
   :password (getenv "MANTIS_DB_PASSWORD" "mantis")})

(def schedule-tasks?
  (Boolean/parseBoolean (getenv "SCHEDULE_TASKS" "true")))

(def metrics-report-interval
  (Long/parseLong (getenv "METRICS_REPORTER_INTERVAL" "5")))
