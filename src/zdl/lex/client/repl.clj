(ns zdl.lex.client.repl
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [nrepl.server :as repl]
            [zdl.lex.env :refer [getenv]]))

(defstate server
  :start (when-let [port (getenv "REPL_PORT")]
           (log/info (format "Starting REPL server @%s/tcp" port))
           (repl/start-server :port (Integer/parseInt port)))
  :stop (some-> server repl/stop-server))
