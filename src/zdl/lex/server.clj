(ns zdl.lex.server
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [zdl.lex.data :as data]
            [zdl.lex.fs :refer [path]]
            [zdl.lex.server.article :as server.article]
            [zdl.lex.server.git :as server.git]
            [zdl.lex.server.http :as server.http]
            [zdl.lex.server.issue :as server.issue]
            [zdl.lex.server.lock :as server.lock]
            [zdl.lex.server.metrics :as server.metrics]
            [zdl.lex.server.solr.suggest :as server.solr.suggest]
            [zdl.lex.util :refer [exec! install-uncaught-exception-handler!]]))

(install-uncaught-exception-handler!)

(def states
  #{#'server.article/scheduled-refresh
    #'server.git/repo
    #'server.git/scheduled-commit
    #'server.git/scheduled-gc
    #'server.http/server
    #'server.issue/scheduled-sync
    #'server.lock/db
    #'server.lock/cleanup
    #'server.metrics/reporter
    #'server.solr.suggest/form-suggestions-update})

(defn start
  []
  (mount/start (mount/only states))
  (log/infof "Started ZDL/Lex Server @[%s]" (path (data/dir))))

(defn stop
  []
  (log/info "Stopping ZDL/Lex Server")
  (mount/stop (mount/only states)))

(defn stop-on-shutdown
  []
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable stop)))

(defn start!
  [& _]
  (exec! (fn [& _]
           (stop-on-shutdown)
           (start)
           (.join (Thread/currentThread)))))

(def -main
  start!)
