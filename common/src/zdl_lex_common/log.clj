(ns zdl-lex-common.log
  (:require [taoensso.timbre :as timbre]
            [zdl-lex-common.env :refer [env]]
            [clojure.string :as str])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

(defn configure-slf4j-bridge []
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install))

(defn configure-timbre []
  (timbre/handle-uncaught-jvm-exceptions!)
  (timbre/set-level! (env :log-level))
  (timbre/merge-config!
   {:ns-blacklist ["clj-soap.client"
                   "httpclient.*"
                   "org.apache.axiom.*"
                   "org.apache.axis2.*"
                   "org.apache.commons.httpclient.*"
                   "org.apache.http.*"
                   "org.eclipse.jetty.*"
                   "org.eclipse.jgit.*"]}))

(defn configure []
  (configure-slf4j-bridge)
  (configure-timbre))
