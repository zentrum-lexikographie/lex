(ns zdl.lex.env
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [taoensso.telemere :as tel]
   [taoensso.telemere.tools-logging :as tel.tools-logging]))

(tel.tools-logging/tools-logging->telemere!)
(tel/uncaught->error!)

(defn getenv
  ([k]
   (getenv k nil))
  ([k df]
   (let [k (str "ZDL_LEX_" (csk/->SCREAMING_SNAKE_CASE_STRING k))]
     (or (some-> (System/getenv k) str/trim not-empty) df))))

(when (getenv "DEBUG") (tel/set-min-level! :debug))
