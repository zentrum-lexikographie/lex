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
   (or (some->> k (str "ZDL_LEX_") System/getProperty str/trim not-empty) df)))
