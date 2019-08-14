(ns zdl-lex-server.git
  (:require [clj-jgit.porcelain :as jgit]
            [clojure.core.async :as async]
            [clojure.java.shell :as sh]
            [clojure.set :refer [union]]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.cron :as cron]
            [zdl-lex-server.store :as store]
            [clojure.string :as str]))

(defn- format-git-result [git-args {:keys [exit out err]}]
  (str "\n" (apply str (map (constantly "-") (range 72)))
       "\n"
       "\n$ git " (str/join " " git-args)
       "\n"
       "\n-> exit code: " exit
       (if (not-empty out) (str "\n\n" out))
       (if (not-empty err) (str "\n\n" err))
       "\n" (apply str (map (constantly "-") (range 72)))))

(defn git-unchecked [& args]
  (locking store/git-dir
    (sh/with-sh-dir store/git-dir
      (let [result (apply sh/sh (concat ["git"] args))]
        (timbre/info (format-git-result args result))
        result))))

(defn git [& args]
  (let [{:keys [exit] :as result} (apply git-unchecked args)]
    (when-not (= exit 0)
      (timbre/warn (format-git-result args result))
      (throw (ex-info (str args) result)))
    result))

(defn changed-files []
  (locking store/git-dir
    (jgit/with-repo store/git-dir
      (->> (jgit/git-status repo) (vals) (apply union)))))

(defn commit* []
  (git "add" ".")
  (git "commit" "-m" "zdl-lex-server"))

(defn commit []
  (locking store/git-dir
    (let [changed-files (changed-files)]
      (when-not (empty? changed-files)
        (commit*)
        changed-files))))

(defn merge-origin []
  (locking store/git-dir
    (git "pull" "-s" "recursive" "-X" "ours" "--no-edit")
    (git "push" "origin")))

(defn- absolute-path [f]
  (->> f (fs/file store/git-dir) fs/absolute fs/normalized))

(defonce changes (async/chan))

(defn- commit-changes []
  (some->> (try (commit) (catch Throwable t #{}))
           (map absolute-path)
           (into (sorted-set))
           (timbre/spy :trace)
           (async/>!! changes)))

(defstate changes-provider
  :start (cron/schedule "*/10 * * * * ?" "Git commit" commit-changes)
  :stop (async/close! changes-provider))

(defstate rebase-scheduler
  :start (cron/schedule "0 0 * * * ?" "Git merge" merge-origin)
  :stop (async/close! rebase-scheduler))

(comment
  (changed-files)
  (commit)
  (merge-origin)
  (println (format-git-result ["status"] (git "status"))))
