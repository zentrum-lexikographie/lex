(ns zdl.lex.server.schedule
  (:require
   [chime.core :as chime]
   [taoensso.telemere :as t]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.issue :as issue]
   [zdl.lex.server.lock :as lock])
  (:import
   (java.time Duration LocalDateTime ZonedDateTime ZoneId)
   (java.time.temporal ChronoUnit)))

(defn periodic-seq
  [start duration]
  (->>
   (chime/periodic-seq start duration)
   (chime/without-past-times)))

(defn day-at-hour
  [^LocalDateTime reference hour]
  (-> (.. reference (truncatedTo ChronoUnit/DAYS) (withHour hour))
      (ZonedDateTime/of (ZoneId/systemDefault))))

(defn today-at-hour
  [hour]
  (day-at-hour (LocalDateTime/now) hour))

(defn at-hour
  [hour]
  (periodic-seq (today-at-hour hour) (Duration/ofDays 1)))

(defn after-every
  [duration]
  (periodic-seq (today-at-hour 0) duration))

(defn task-error-handler
  [e]
  (t/error! e)
  (not (instance? InterruptedException e)))

(defn schedule
  [desc times f]
  (t/with-ctx+ {::schedule desc}
    (chime/chime-at
     times
     (fn [ts] (t/event! {::timestamp ts}) (f))
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
                            (after-every (Duration/ofMinutes 5))
                            lock/cleanup!)
    :git-commit   (schedule "Git Commit"
                            (after-every (Duration/ofMinutes 15))
                            git/commit!)
    :issue-sync   (schedule "Mantis Issue Sync"
                            (after-every (Duration/ofMinutes 15))
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
