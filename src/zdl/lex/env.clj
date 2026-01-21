(ns zdl.lex.env
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [lambdaisland.uri :as uri]
   [taoensso.telemere :as tm]
   [taoensso.telemere.tools-logging :as tm.tools-logging]
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.data.csv :as csv])
  (:import
   (com.codahale.metrics Meter MetricRegistry Slf4jReporter Timer)
   (io.github.cdimascio.dotenv Dotenv)
   (java.util.concurrent TimeUnit)))

(tm.tools-logging/tools-logging->telemere!)
(tm/uncaught->error!)
(tm/set-min-level! :info)
(tm/set-min-level! nil "com.rabbitmq.client.TrustEverythingTrustManager" :error)

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
  (getenv "SERVER_URL" "https://labor.dwds.de"))

(def server-user
  (getenv "SERVER_USER"))

(def server-password
  (getenv "SERVER_PASSWORD"))

(def server-auth
  (when (and server-user server-password) [server-user server-password]))

(def repl-port
  (parse-long (getenv "REPL_PORT" "3001")))

(def git-origin
  (getenv "GIT_ORIGIN" "git@git.zdl.org:zdl/dict.git"))

(def git-branch
  (getenv "GIT_BRANCH" "dev"))

(def git-dir
  (getenv "GIT_DIR" "/data/git"))

(def http-port
  (parse-long (getenv "HTTP_PORT" "3000")))

(def solr-url
  (uri/join (getenv "SOLR_URL" "http://index:8983/solr/")
            (str (getenv "SOLR_CORE" "articles") "/")))

(def db
  {:host          (getenv "DB_HOST" "db")
   :port          (parse-long (getenv "DB_PORT" "5432"))
   :database      (getenv "DB_NAME" "lex")
   :user          (getenv "DB_USER" "lex")
   :password      (getenv "DB_PASSWORD" "lex")
   :ssl?          true
   :migrations-path "zdl/lex/server/db"
   :pool-max-size 8})

(def queue
  {:host     (getenv "QUEUE_HOST" "labor.dwds.de")
   :port     (Integer/parseInt (getenv "QUEUE_PORT" "5671"))
   :user     (getenv "QUEUE_USER" "lex")
   :password (getenv "QUEUE_PASSWORD" "lex")})

(def gpt-queue-name
  (getenv "QUEUE_GPT_QUEUE" "gpt"))

(def mantis-db
  {:dbtype   "mysql"
   :host     (getenv "MANTIS_DB_HOST" "mantis.dwds.de")
   :port     (parse-long (getenv "MANTIS_DB_PORT" "3306"))
   :dbname   (getenv "MANTIS_DB_NAME" "mantis_bugtracker")
   :username (getenv "MANTIS_DB_USER" "mantis")
   :password (getenv "MANTIS_DB_PASSWORD" "mantis")})

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
       (tm/log! {:level :warn :id ::userbase :data fallback-userbase})
       fallback-userbase))))

(def schedule-tasks?
  (Boolean/parseBoolean (getenv "SCHEDULE_TASKS" "true")))

(def metrics-report-interval
  (parse-long (getenv "METRICS_REPORTER_INTERVAL" "5")))

(def ^MetricRegistry metrics-registry
  (MetricRegistry.))

(def ^:dynamic metrics-reporter
  nil)

(defn stop-metrics-reporter
  []
  (when metrics-reporter
    (.close metrics-reporter)
    (alter-var-root #'metrics-reporter (constantly nil))))

(defn start-metrics-reporter
  []
  (stop-metrics-reporter)
  (->>
   (doto (.build (Slf4jReporter/forRegistry metrics-registry))
     (.start metrics-report-interval TimeUnit/MINUTES))
   (constantly)
   (alter-var-root #'metrics-reporter)))

(defn meter
  [k]
  (.meter metrics-registry k))

(defn timer
  [k]
  (.timer metrics-registry k))

(defn metered!
  [^Meter meter]
  (.mark meter))

(defn timed!
  [^Timer timer]
  (.time timer))
