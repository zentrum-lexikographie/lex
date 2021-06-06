(ns zdl.lex.server.tasks
  (:require [manifold.deferred :as d]
            [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [zdl.lex.cron :as cron]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.graph.mantis :as mantis]
            [zdl.lex.server.lock :as lock]
            [zdl.lex.server.solr.client :as solr-client]))

(defn trigger-schedule
  [s]
  (d/chain
   (s/put! s "<HTTP-Trigger>")
   (fn [put?] {:status 200 :body {:triggered put?}})))

(defstate lock-cleanup
  :start (cron/schedule-stream "0 */5 * * * ?" "Lock cleanup" lock/cleanup)
  :stop (s/close! lock-cleanup))

(defstate git-commit
  :start (cron/schedule-stream "0 */15 * * * ?" "Git commit" git/commit!)
  :stop (s/close! git-commit))

(defn trigger-git-commit
  [_]
  (trigger-schedule git-commit))

(defstate git-gc
  :start (cron/schedule-stream "0 0 5 * * ?" "Git Garbage Collection" git/gc!)
  :stop (s/close! git-gc))

(defstate form-suggestions-update
  :start (cron/schedule-stream "0 */10 * * * ?" "Form Suggestions FSA update"
                               solr-client/build-forms-suggestions)
  :stop (s/close! form-suggestions-update))

(defstate articles-refresh
  :start (cron/schedule-stream "0 0 1 * * ?" "Refresh all articles"
                               article/refresh-articles!)
  :stop (s/close! articles-refresh))

(defn trigger-articles-refresh
  [_]
  (trigger-schedule articles-refresh))

(defstate mantis-sync
  :start (cron/schedule-stream "0 */15 * * * ?" "Mantis Synchronization"
                               mantis/issues->db!)
  :stop (s/close! mantis-sync))

(defn trigger-mantis-sync
  [_]
  (trigger-schedule mantis-sync))

