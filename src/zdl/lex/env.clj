(ns zdl.lex.env
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [lambdaisland.uri :as uri]
   [taoensso.telemere :as tm]
   [taoensso.telemere.tools-logging :as tm.tools-logging])
  (:import
   (io.github.cdimascio.dotenv Dotenv)))

(tm.tools-logging/tools-logging->telemere!)
(tm/uncaught->error!)
(tm/set-min-level! :info)

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

(def solr-url
  (uri/join (getenv "SOLR_URL" "http://index:8983/solr/")
            (str (getenv "SOLR_CORE" "articles") "/")))
