(ns zdl.lex.server.schedule
  (:require
   [chime.core :as chime]
   [taoensso.telemere :as tm]
   [tick.core :as t]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.issue :as issue]
   [zdl.lex.server.lock :as lock]))

(defn periodic-seq
  [start duration]
  (->>
   (chime/periodic-seq start duration)
   (chime/without-past-times)))

(defn day-at-hour
  [reference hour]
  (-> reference
      (t/truncate :days)
      (t/in "Europe/Berlin")
      (t/with :hour-of-day hour)))

(defn today-at-hour
  [hour]
  (day-at-hour (t/offset-date-time) hour))

(defn at-hour
  [hour]
  (periodic-seq (today-at-hour hour) (t/of-days 1)))

(defn after-every
  [duration]
  (periodic-seq (today-at-hour 0) duration))

(defn task-error-handler
  [e]
  (tm/error! e)
  (not (instance? InterruptedException e)))

(defn schedule
  [desc times f]
  (tm/with-ctx+ {::schedule desc}
    (chime/chime-at
     times
     (fn [ts] (tm/event! {::timestamp ts}) (f))
     {:error-handler task-error-handler})))

(def ^:dynamic tasks
  nil)

(defn stop
  []
  (when tasks
    (doseq [task (vals tasks)] (.close task))
    (alter-var-root #'tasks (constantly nil))))

(defn start
  []
  (stop)
  (->>
   {:lock-cleanup (schedule "Lock Database Cleanup"
                            (after-every (t/of-minutes 5))
                            lock/cleanup!)
    :git-commit   (schedule "Git Commit"
                            (after-every (t/of-minutes 15))
                            git/commit!)
    :issue-sync   (schedule "Mantis Issue Sync"
                            (after-every (t/of-minutes 15))
                            issue/sync!)
    :article-edit (schedule "Article QA"
                            (at-hour 1)
                            git/qa!)
    :git-refresh  (schedule "Git/Index Sync"
                            (at-hour 3)
                            git/sync-index!)
    :git-gc       (schedule "Git Garbage Collection"
                            (at-hour 5)
                            git/gc!)}
   (constantly)
   (alter-var-root #'tasks)))
