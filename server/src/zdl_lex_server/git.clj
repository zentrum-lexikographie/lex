(ns zdl-lex-server.git
  (:require [clj-jgit.porcelain :as jgit]
            [clojure.core.async :as async]
            [clojure.java.shell :as sh]
            [clojure.set :refer [union]]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.cron :as cron]
            [zdl-lex-server.store :as store]))

(defn git [& args]
  (locking store/git-dir
    (sh/with-sh-dir store/git-dir
      (let [result (apply sh/sh (concat ["git"] args))
            exit-code (result :exit)
            succeeded (= exit-code 0)]
        (timbre/log (if succeeded :trace :warn) {:git args :result result})
        (when-not succeeded (throw (ex-info (str args) result)))
        result))))

(defn changed-files []
  (locking store/git-dir
    (jgit/with-repo store/git-dir
      (->> (jgit/git-status repo) (vals) (apply union)))))

(defn commit []
  (locking store/git-dir
    (let [changed-files (changed-files)]
      (when-not (empty? changed-files)
        (git "add" ".")
        (git "commit" "-q" "-m" "zdl-lex-server")
        changed-files))))

(defn rebase []
  (locking store/git-dir
    (git "fetch" "-q" "origin")
    (git "rebase" "-q" "-s" "recursive" "-X" "theirs")
    (git "push" "-q" "origin")))

(defn- absolute-path [f]
  (->> f (fs/file store/git-dir) fs/absolute fs/normalized))

(defonce changes (async/chan))

(defstate changes-provider
  :start (let [schedule (cron/parse "*/10 * * * * ?")
               stop-ch (async/chan)]
           (async/go-loop []
             (when (async/alt! (async/timeout (cron/millis-to-next schedule)) :tick
                               stop-ch nil)
               (some->> (async/<!
                         (async/thread (try (commit) (catch Throwable t #{}))))
                        (map absolute-path)
                        (into (sorted-set))
                        (timbre/spy :trace)
                        (async/>! changes))
               (recur)))
           stop-ch)
  :stop (async/close! changes-provider))

(defstate rebase-scheduler
  :start (let [schedule (cron/parse "0 0 * * * ?")
               ch (async/chan)]
           (async/go-loop []
             (when (async/<! (async/timeout (cron/millis-to-next schedule)))
               (async/<!
                (async/thread
                  (try
                    (rebase)
                    (catch Throwable t (timbre/warn t)))))
               (recur)))
           ch)
  :stop (async/close! rebase-scheduler))
