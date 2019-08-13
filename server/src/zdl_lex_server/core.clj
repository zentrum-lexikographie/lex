(ns zdl-lex-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.http :as http]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]
            [clojure.core.async :as async]
            [zdl-lex-server.exist :as exist]
            [zdl-lex-server.mantis :as mantis]
            [zdl-lex-common.article :as article])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

(defn configure-logging []
  (SLF4JBridgeHandler/removeHandlersForRootLogger)
  (SLF4JBridgeHandler/install)
  (timbre/handle-uncaught-jvm-exceptions!)
  (timbre/set-level! (config :log-level))
  (timbre/merge-config! (config :log-config)))

(configure-logging)

(defn -main []
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))
  (timbre/info (mount/start)))

(comment
  (exist/xquery "xmldb:get-current-user()")
  (async/>!! exist/exist->git "DWDS/billes-006/Nachbarland.xml")
  (git/rebase)
  http/server
  (time (->> (store/article-files) (drop 150000) (take 3)))
  (@mantis/index "Sinnkrise")
  (-> (solr/sync-articles) (last))
  (solr/commit-optimize)
  (async/>!! solr/git-all->solr :sync))
