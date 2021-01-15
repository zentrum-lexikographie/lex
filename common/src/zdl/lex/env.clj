(ns zdl.lex.env
  (:require [clojure.string :as str]
            [mount.core :as mount])
  (:import io.github.cdimascio.dotenv.Dotenv))

;; load `.env` into system properties
(.. Dotenv (configure) (ignoreIfMissing) (systemProperties) (load))

(defn getenv
  ([k]
   (getenv k nil))
  ([k df]
   (let [k (str "ZDL_LEX_" k)]
     (or (some->> k System/getProperty str/trim not-empty)
         (some->> k System/getenv str/trim not-empty)
         df))))
