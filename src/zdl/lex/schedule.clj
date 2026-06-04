(ns zdl.lex.schedule
  (:require
   [chime.core :as chime]
   [integrant.core :as ig]
   [ring.util.response :as resp]
   [taoensso.telemere :as tel]
   [tick.core :as t]
   [zdl.lex.git :as git]
   [zdl.lex.index :as index]
   [zdl.lex.issue :as issue]
   [zdl.lex.lock :as lock]
   [zdl.lex.qa :as qa]))

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
  (tel/error! e)
  (not (instance? InterruptedException e)))

(defn schedule
  [desc times f]
  (tel/with-ctx+ {::schedule desc}
    (chime/chime-at
     times
     (fn [ts] (tel/event! {:data {::timestamp ts}}) (f))
     {:error-handler task-error-handler})))

(defmethod ig/init-key ::tasks
  [_ {:keys [db repo]}]
  [(schedule "Lock Database Cleanup"
             (after-every (t/of-minutes 5))
             #(lock/cleanup! db))
   (schedule "Git Commit"
             (after-every (t/of-minutes 15))
             #(git/commit! repo))
   (schedule "Mantis Issue Sync"
             (after-every (t/of-minutes 15))
             #(issue/sync!))
   (schedule "Article QA"
             (at-hour 1)
             (partial qa/edit-articles! db))
   (schedule "Git/Index Sync"
             (at-hour 3)
             index/sync-articles!)
   (schedule "Git Garbage Collection"
             (at-hour 5)
             #(git/gc! repo))])

(defmethod ig/halt-key! ::tasks
  [_ tasks]
  (doseq [task tasks] (.close task)))

(defn trigger-task
  [task]
  (fn [_]
    (future (try (task) (catch Throwable t (tel/error! ::task t))))
    (resp/response {:triggered true})))
