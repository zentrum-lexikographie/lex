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
(tm/set-min-level! nil "com.zaxxer(.*)" :warn)

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
  (Long/parseLong (getenv "REPL_PORT" "3001")))

(def git-origin
  (getenv "GIT_ORIGIN" "git@git.zdl.org:zdl/dict.git"))

(def git-branch
  (getenv "GIT_BRANCH" "dev"))

(def git-dir
  (getenv "GIT_DIR" "/data/git"))

(def http-port
  (Long/parseLong (getenv "HTTP_PORT" "3000")))

(def solr-url
  (uri/join (getenv "SOLR_URL" "http://index:8983/solr/")
            (str (getenv "SOLR_CORE" "articles") "/")))

(def db
  (let [user (getenv "DB_USER" "nlp")]
    {:dbtype   "postgresql"
     :host     (getenv "DB_HOST" "db")
     :dbname   (getenv "DB_NAME" "nlp")
     :user     user
     :username user
     :password (getenv "DB_PASSWORD" "nlp")}))

(def mantis-db
  {:dbtype   "mysql"
   :host     (getenv "MANTIS_DB_HOST" "mantis.dwds.de")
   :port     (Long/parseLong (getenv "MANTIS_DB_PORT" "3306"))
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

(def ddc-dstar-request
  {:request-method :get
   :url            "https://ddc.dwds.de/dstar/"
   :basic-auth     [(getenv "DDC_DSTAR_USER" "ddc")
                    (getenv "DDC_DSTAR_PASSWORD" "ddc")]})

(def korap-dereko-request
  {:request-method :get
   :url            "https://korap.ids-mannheim.de/api/v1.0/search"
   :oauth-token    (getenv "DEREKO_ACCESS_TOKEN" "")})

(def korap-dnb-request
  {:request-method :get
   :url            "https://korap.dnb.de/api/v1.0/search"})

(def openai-api-request
  {:request-method :post
   :url            "https://api.openai.com/v1/chat/completions"
   :headers        {"Content-Type" "application/json"
                    "Accept"       "application/json"}
   :oauth-token    (getenv "OPENAI_API_TOKEN")
   :body           {"model"       "gpt-4.1-2025-04-14"
                    "temperature" 0.0
                    "top_p"       0.75}})

(def schedule-tasks?
  (Boolean/parseBoolean (getenv "SCHEDULE_TASKS" "true")))

(def metrics-report-interval
  (Long/parseLong (getenv "METRICS_REPORTER_INTERVAL" "5")))

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

