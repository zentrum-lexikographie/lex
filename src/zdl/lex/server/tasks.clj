(ns zdl.lex.server.tasks
  (:require [clojure.core.async :as a]
            [mount.core :refer [defstate]]
            [zdl.lex.cron :as cron]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.graph.mantis :as mantis]
            [zdl.lex.server.lock :as lock]
            [zdl.lex.server.solr.client :as solr-client]))

(defn trigger-schedule
  [ch]
  (a/go {:status 200 :body {:triggered (a/>! ch :trigger)}}))

(defstate lock-cleanup
  :start (cron/schedule "0 */5 * * * ?" "Lock cleanup" lock/cleanup)
  :stop (a/close! lock-cleanup))

(defstate git-commit
  :start (cron/schedule "0 */15 * * * ?" "Git commit" git/commit!)
  :stop (a/close! git-commit))

(defn trigger-git-commit
  [_]
  (trigger-schedule git-commit))

(defstate git-gc
  :start (cron/schedule "0 0 5 * * ?" "Git Garbage Collection" git/gc!)
  :stop (a/close! git-gc))

(defstate form-suggestions-update
  :start (cron/schedule "0 */10 * * * ?" "Form Suggestions FSA update"
                        solr-client/build-forms-suggestions)
  :stop (a/close! form-suggestions-update))

(defstate articles-refresh
  :start (cron/schedule "0 0 1 * * ?" "Refresh all articles"
                        article/refresh-articles!)
  :stop (a/close! articles-refresh))

(defn trigger-articles-refresh
  [_]
  (trigger-schedule articles-refresh))

(defstate mantis-sync
  :start (cron/schedule "0 */15 * * * ?" "Mantis Synchronization"
                        mantis/issues->db!)
  :stop (a/close! mantis-sync))

(defn trigger-mantis-sync
  [_]
  (trigger-schedule mantis-sync))

