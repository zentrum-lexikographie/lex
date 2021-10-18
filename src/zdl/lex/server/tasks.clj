(ns zdl.lex.server.tasks
  (:require [clojure.core.async :as a]
            [mount.core :refer [defstate]]
            [zdl.lex.cron :as cron]
            [zdl.lex.server.article :as server.article]
            [zdl.lex.server.git :as server.git]
            [zdl.lex.server.graph.mantis :as graph.mantis]
            [zdl.lex.server.lock :as lock]
            [zdl.lex.server.solr.suggest :as solr.suggest]))

(defn trigger!
  [ch]
  {:status 200 :body {:triggered (some? (cron/trigger! ch))}})

(defstate lock-cleanup
  :start (cron/schedule "0 */5 * * * ?" "Lock cleanup" lock/cleanup)
  :stop (a/close! lock-cleanup))

(defstate git-commit
  :start (cron/schedule "0 */15 * * * ?" "Git commit" server.git/commit!)
  :stop (a/close! git-commit))

(defn trigger-git-commit
  [_]
  (trigger! git-commit))

(defstate git-gc
  :start (cron/schedule "0 0 5 * * ?" "Git Garbage Collection" server.git/gc!)
  :stop (a/close! git-gc))

(defstate form-suggestions-update
  :start (cron/schedule "0 */10 * * * ?" "Form Suggestions FSA update"
                        solr.suggest/build-forms-suggestions)
  :stop (a/close! form-suggestions-update))

(defstate articles-refresh
  :start (cron/schedule "0 0 1 * * ?" "Refresh all articles"
                        server.article/refresh!)
  :stop (a/close! articles-refresh))

(defn trigger-articles-refresh
  [_]
  (trigger! articles-refresh))

(defstate mantis-sync
  :start (cron/schedule "0 */15 * * * ?" "Mantis Synchronization"
                        graph.mantis/update-graph)
  :stop (a/close! mantis-sync))

(defn trigger-mantis-sync
  [_]
  (trigger! mantis-sync))

