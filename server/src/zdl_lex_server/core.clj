(ns zdl-lex-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.env :refer [config]])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

(SLF4JBridgeHandler/removeHandlersForRootLogger)
(SLF4JBridgeHandler/install)
(timbre/handle-uncaught-jvm-exceptions!)
(timbre/set-level! (config :log-level))
(timbre/merge-config! (config :log-config))

(require 'zdl-lex-server.git)
(require 'zdl-lex-server.http)
(require 'zdl-lex-server.solr)
(require 'zdl-lex-server.store)

(defn -main []
  (mount/start)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))
  (.join zdl-lex-server.http/server))
