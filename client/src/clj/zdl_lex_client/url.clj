(ns zdl-lex-client.url
  (:require [cemerick.url :refer [url]]
            [zdl-lex-client.env :refer [config]]))

(def server-base (config :server-base))

(defn article [id]
  (url server-base "/articles" id))
