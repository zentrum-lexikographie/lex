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
            [clojure.core.async :as async])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

(defn configure-logging []
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)
  (timbre/handle-uncaught-jvm-exceptions!)
  (timbre/set-level! (config :log-level))
  (timbre/merge-config! (config :log-config)))

(defn -main []
  (configure-logging)
  (mount/start)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents)))))

(comment
  (git/rebase)
  http/server
  (->> (store/article-files) (take 10) solr/add-articles)
  (solr/commit-optimize)
  (async/>!! sync/git-all->solr :sync))
