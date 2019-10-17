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

(defn -main []
  (log/configure)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))
  (timbre/info (env->str env))
  (timbre/info (mount/start
                #'lock/db
                #'lock/lock-cleanup-scheduler
                #'git/git-dir
                #'git/articles-dir
                #'git/commit-scheduler
                #'solr/index-rebuild-scheduler
                #'solr/index-init
                #'solr/git-change-indexer
                #'solr/build-suggestions-scheduler
                #'solr/export-cleanup-scheduler
                #'mantis/issue-sync-scheduler
                #'http/server)))
