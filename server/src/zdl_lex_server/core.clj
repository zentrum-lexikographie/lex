(ns zdl-lex-server.core
  (:require [taoensso.timbre :as timbre]
            [mount.core :as mount]
            [zdl-lex-server.http :as http])
  (:import [org.slf4j.bridge SLF4JBridgeHandler])
  (:gen-class))

(defn -main []
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)

  (timbre/handle-uncaught-jvm-exceptions!)

  (mount/start)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))

  (.join http/server))
