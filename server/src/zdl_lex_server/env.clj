(ns zdl-lex-server.env
  (:require [cprop.core :refer [load-config]]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

(def config (load-config))

(defstate ^{:on-reload :noop} logging
  :start (do
           (SLF4JBridgeHandler/removeHandlersForRootLogger)
           (SLF4JBridgeHandler/install)
           (timbre/handle-uncaught-jvm-exceptions!)
           (timbre/set-level! (config :log-level))
           (timbre/merge-config! {:ns-blacklist ["org.apache.http.*"
                                                 "org.eclipse.jetty.*"
                                                 "org.eclipse.jgit.*"]})))


