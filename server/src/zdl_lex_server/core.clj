(ns zdl-lex-server.core
  (:require [taoensso.timbre :as timbre]
            [mount.core :as mount]
            [zdl-lex-server.api :as api])
  (:import [org.slf4j.bridge SLF4JBridgeHandler])
  (:gen-class))

(defn -main []
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)
  (timbre/handle-uncaught-jvm-exceptions!)
  (timbre/set-level! :info)
  (mount/start)
  (.join api/server))
