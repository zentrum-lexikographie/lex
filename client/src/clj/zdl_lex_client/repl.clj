(ns zdl-lex-client.repl
  (:require [mount.core :refer [defstate]]
            [nrepl.server :as repl]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.env :refer [env]]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defstate server
  :start (when-let [port (env :repl-port)]
           (timbre/info (format "Starting REPL server @%s/tcp" port))
           (repl/start-server :port port :handler (nrepl-handler)))
  :stop (some-> server repl/stop-server))
