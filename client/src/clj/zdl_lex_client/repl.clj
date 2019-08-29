(ns zdl-lex-client.repl
  (:require [mount.core :refer [defstate]]
            [nrepl.server :as repl]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defstate server
  :start (when-let [port (some-> (env :zdl-lex-repl-port) Integer/parseInt)]
           (timbre/info (format "Starting REPL server @%s/tcp" port))
           (repl/start-server :port port :handler (nrepl-handler)))
  :stop (some-> server repl/stop-server))
