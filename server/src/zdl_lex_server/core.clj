(ns zdl-lex-server.core
  (:require [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.env :refer [env env->str]]
            [zdl-lex-common.log :as log]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.http :as http]
            [zdl-lex-server.lock :as lock]
            [zdl-lex-server.mantis :as mantis]
            [zdl-lex-server.solr :as solr]))

(def data-sources [#'lock/db #'git/repo])

(def services [#'http/server])

(def background-tasks [#'lock/lock-cleanup-scheduler
                       #'git/commit-scheduler
                       #'solr/index-rebuild-scheduler
                       #'solr/index-init
                       #'solr/git-change-indexer
                       #'solr/build-suggestions-scheduler
                       #'mantis/issue-sync-scheduler])

(defn -main []
  (log/configure)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))
  (timbre/info (env->str env))
  (let [states (apply mount/start (concat data-sources background-tasks services))]
    (timbre/info states)))

(comment
  (apply mount/start data-sources)
  (apply mount/start services)
  (apply mount/start (concat data-sources services))
  (mount/stop))
