(ns zdl.lex.server.task
  (:require
   [integrant.core :as ig]
   [zdl.lex.cron :as cron :refer [after-every at-hour]]
   [zdl.lex.server.article.lock :as server.article.lock]
   [zdl.lex.server.article.editor :as server.article.editor]
   [zdl.lex.server.git :as server.git]
   [zdl.lex.server.issue :as server.issue]
   [zdl.lex.server.solr.suggest :as solr.suggest])
  (:import
   (java.time Duration)))

(defmethod ig/init-key ::schedule
  [_  {:keys [lock-db issue-db] {git-dir :dir :as git-repo} :git-repo}]
  {:lock-cleanup (cron/schedule "Lock Database Cleanup"
                                 (after-every (Duration/ofMinutes 5))
                                 (partial server.article.lock/cleanup! lock-db))
   :form-suggest (cron/schedule "Form Suggester Rebuild"
                                (after-every (Duration/ofMinutes 10))
                                solr.suggest/build-forms-suggestions!)
   :git-commit   (cron/schedule "Git Commit"
                                 (after-every (Duration/ofMinutes 15))
                                 (partial server.git/commit! git-repo))
   :issue-sync   (cron/schedule "Mantis Issue Sync"
                                 (after-every (Duration/ofMinutes 15))
                                 (partial server.issue/sync! issue-db))
   :article-edit (cron/schedule "Article Editing"
                                 (at-hour 1)
                                 (partial server.article.editor/edit!
                                          git-dir lock-db))
   :git-refresh  (cron/schedule "Git Refresh Article Index"
                                 (at-hour 3)
                                 (partial server.git/refresh! git-dir))
   :git-gc       (cron/schedule "Git Garbage Collection"
                                 (at-hour 5)
                                 (partial server.git/gc! git-dir))})

(defmethod ig/halt-key! ::schedule
  [_ tasks]
  (doseq [schedule (vals tasks)] (cron/stop! schedule)))
