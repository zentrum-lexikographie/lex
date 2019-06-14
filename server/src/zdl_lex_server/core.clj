(ns zdl-lex-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.http :as http]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]
            [zdl-lex-server.sync :as sync]
            [clojure.core.async :as async]
            [zdl-lex-server.exist :as exist]
            [zdl-lex-server.article :as article])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

(defn configure-logging []
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)
  (timbre/handle-uncaught-jvm-exceptions!)
  (timbre/set-level! (config :log-level))
  (timbre/merge-config! (config :log-config)))

(defn -main []
  (configure-logging)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))
  (timbre/info (mount/start)))

(comment
  (exist/xquery "xmldb:get-current-user()")
  (async/>!! exist/exist->git "DWDS/billes-006/Nachbarland.xml")
  (git/rebase)
  http/server
  (time (->> (store/article-files) (pmap article/document) last))
  (solr/commit-optimize)
  (async/>!! sync/git-all->solr :sync))
