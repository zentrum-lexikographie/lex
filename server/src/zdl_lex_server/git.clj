(ns zdl-lex-server.git
  (:require [clj-jgit.porcelain :as jgit]
            [clojure.core.async :as async]
            [clojure.java.shell :as sh]
            [clojure.set :refer [union]]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.bus :as bus]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store]
            [me.raynes.fs :as fs]))

(defn git [& args]
  (locking store/git-dir
    (sh/with-sh-dir store/git-dir
      (let [result (apply sh/sh (concat ["git"] args))
            exit-code (result :exit)
            succeeded (= exit-code 0)]
        (timbre/log (if succeeded :debug :warn) {:git args :result result})
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

(defstate changes
  :start (let [stop-ch (async/chan)
               interval (config :git-commit-interval)]
           (async/go-loop [i 0]
             (when (async/alt! (async/timeout interval) :tick stop-ch nil)
               (try
                 (some->> (commit)
                          (map absolute-path)
                          (into (sorted-set))
                          (async/>! bus/git-changes))
                 (catch Throwable t))
               (when (= 0 (mod i 10))
                 (try
                   (rebase)
                   (catch Throwable t)))
               (recur (inc i))))
           stop-ch)
  :stop (async/close! changes))
