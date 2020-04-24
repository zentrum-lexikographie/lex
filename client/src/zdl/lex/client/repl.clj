(ns zdl.lex.client.repl
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [nrepl.server :as repl]
            [zdl.lex.env :refer [getenv]]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defstate server
  :start (when-let [port (getenv "ZDL_LEX_REPL_PORT")]
           (log/info (format "Starting REPL server @%s/tcp" port))
           (repl/start-server :port (Integer/parseInt port)
                              :handler (nrepl-handler)))
  :stop (some-> server repl/stop-server))
