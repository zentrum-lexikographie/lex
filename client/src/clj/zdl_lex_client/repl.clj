(ns zdl-lex-client.repl
  (:require [mount.core :refer [defstate]]
            [nrepl.server :as repl]
            [zdl-lex-client.env :refer [config]]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defstate server
  :start (when-let [port (get-in config [:repl :port])]
           (repl/start-server :port port :handler (nrepl-handler)))
  :stop (some-> server repl/stop-server))
