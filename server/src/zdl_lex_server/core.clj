(ns zdl-lex-server.core
  (:require [taoensso.timbre :as timbre]
            [mount.core :as mount :refer [defstate]]
            [zdl-lex-server.http :as http])
  (:import [org.slf4j.bridge SLF4JBridgeHandler])
  (:gen-class))

(defstate ^{:on-reload :noop} logging
  :start (do
           (SLF4JBridgeHandler/removeHandlersForRootLogger)
           (SLF4JBridgeHandler/install)
           (timbre/set-level! (read-string (or (System/getenv "TIMBRE_LEVEL")
                                               ":debug")))
           (timbre/merge-config! {:ns-blacklist ["org.apache.http.*"
                                                 "org.eclipse.jetty.*"
                                                 "org.eclipse.jgit.*"]})))

(defn -main []
  (timbre/handle-uncaught-jvm-exceptions!)
  (mount/start)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))
  (.join http/server))
