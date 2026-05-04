(ns zdl.lex.env
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [taoensso.telemere :as tm]
   [taoensso.telemere.tools-logging :as tm.tools-logging]))

(tm.tools-logging/tools-logging->telemere!)
(tm/uncaught->error!)
(tm/set-min-level! :info)

(defn getenv
  ([k]
   (getenv k nil))
  ([k df]
   (let [k (str "ZDL_LEX_" (csk/->SCREAMING_SNAKE_CASE_STRING k))]
     (or (some-> (System/getenv k) str/trim not-empty) df))))
