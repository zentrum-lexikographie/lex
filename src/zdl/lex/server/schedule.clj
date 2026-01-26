(ns zdl.lex.server.schedule
  (:require
   [chime.core :as chime]
   [integrant.core :as ig]
   [ring.util.response :as resp]
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
     (fn [ts] (tm/event! {:data {::timestamp ts}}) (f))
     {:error-handler task-error-handler})))

(defmethod ig/init-key ::tasks
  [_ {:keys [db issue-db repo]}]
  [(schedule "Lock Database Cleanup"
             (after-every (t/of-minutes 5))
             #(lock/cleanup! db))
   (schedule "Git Commit"
             (after-every (t/of-minutes 15))
             #(git/commit! repo))
   (schedule "Mantis Issue Sync"
             (after-every (t/of-minutes 15))
             #(issue/sync! issue-db))
   (schedule "Article QA"
             (at-hour 1)
             (partial git/qa! db repo))
   (schedule "Git/Index Sync"
             (at-hour 3)
             #(git/sync-index! repo))
   (schedule "Git Garbage Collection"
             (at-hour 5)
             #(git/gc! repo))])

(defmethod ig/halt-key! ::tasks
  [_ tasks]
  (doseq [task tasks] (.close task)))

(defn trigger-task
  [task]
  (fn [_]
    (future (try (task) (catch Throwable t (tm/error! t))))
    (resp/response {:triggered true})))

(defn handlers
  [db issue-db repo]
  [""
   ["/commit"
    {:patch {:summary "Commit pending changes on the server's branch"
             :tags    ["Article" "Git" "Admin"]
             :handler (trigger-task (partial git/commit! repo))}}]
   ["/git/:ref"
    {:post  {:summary    "Fast-forwards the server's branch to the given ref"
             :tags       ["Article" "Git" "Admin"]
             :parameters {:path [:map [:ref :string]]}
             :handler    (partial git/handle-fast-forward repo)}
     :patch {:summary    "Rebases the server's branch to the given ref"
             :tags       ["Article" "Git" "Admin"]
             :parameters {:path [:map [:ref :string]]}
             :handler    (partial git/handle-rebase repo)}}]
   ["/qa"
    {:patch {:summary "Edits article data"
             :tags    ["Article", "Git", "Admin"]
             :handler (trigger-task (partial git/qa! db repo))}}]
   ["/index"
    {:patch {:summary "Refreshes all article data in index"
             :tags    ["Index", "Admin"]
             :handler (trigger-task (partial git/sync-index! repo))}}]
   ["/issues"
    {:patch {:summary "Clears the Mantis issue index and re-synchronizes it"
             :tags    ["Mantis" "Admin"]
             :handler (trigger-task (partial issue/sync! issue-db))}}]])
