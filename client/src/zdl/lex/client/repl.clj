(ns zdl.lex.client.repl
  (:require [mount.core :refer [defstate]]
            [nrepl.server :as repl]
            [clojure.tools.logging :as log]
            [zdl.lex.env :refer [env]]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defstate server
  :start (when-let [port (env :repl-port)]
           (log/info (format "Starting REPL server @%s/tcp" port))
           (repl/start-server :port port :handler (nrepl-handler)))
  :stop (some-> server repl/stop-server))
