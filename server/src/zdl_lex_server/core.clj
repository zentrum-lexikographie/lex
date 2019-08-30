(ns zdl-lex-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.http :as http]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]
            [clojure.core.async :as a]
            [zdl-lex-server.exist :as exist]
            [zdl-lex-server.mantis :as mantis]
            [zdl-lex-common.log :as log]
            [zdl-lex-common.article :as article])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

(log/configure-slf4j-bridge)
(log/configure-timbre)

(defn -main []
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))
  (timbre/info (mount/start)))

(comment
  (exist/xquery "xmldb:get-current-user()")
  (git/rebase)
  http/server
  (time (->> (store/article-files) (drop 150000) (take 3)))
  (@mantis/index "Sinnkrise")
  (-> (solr/sync-articles) (last))
  (solr/commit-optimize)
  (a/>!! solr/git-all->solr :sync))
