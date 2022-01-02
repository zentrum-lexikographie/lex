(ns zdl.lex.server.git
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [metrics.timers :as timers]
            [mount.core :refer [defstate]]
            [slingshot.slingshot :refer [try+ throw+]]
            [zdl.lex.data :as data]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :refer [file path file?]]
            [zdl.lex.git :as git]
            [zdl.lex.server.article :as server.article]
            [zdl.lex.cron :as cron]
            [clojure.core.async :as a])
  (:import java.io.File
           java.util.concurrent.locks.ReentrantLock
           java.util.concurrent.TimeUnit))

(def dir
  (data/dir "git"))

(def origin
  (getenv "GIT_ORIGIN"))

(def branch
  (getenv "GIT_BRANCH" "zdl-lex-server/development"))

(def lock
  (ReentrantLock.))

(def default-timeout
  30000)

(defn lock!
  ([]
   (lock! default-timeout))
  ([timeout]
   (.tryLock lock timeout TimeUnit/MILLISECONDS)))

(defn unlock!
  []
  (.unlock lock))

(defn with-lock
  ([f]
   (with-lock default-timeout f))
  ([timeout f]
   (when-not (lock! timeout)
     (throw+ {:type ::lock-timeout ::timeout timeout ::f f}))
   (try (f) (finally (unlock!)))))

(defn publish-changes!
  [paths]
  (when (seq paths)
    (let [files   (map #(file dir %) paths)
          updated (filter #(.exists ^File %) files)
          removed (remove #(.exists ^File %) files)]
      (when (seq updated)
        (server.article/update! updated))
      (when (seq removed)
        (server.article/remove! removed))
      files)))

(def gc-timer
  (timers/timer ["git" "local" "gc-timer"]))

(defn gc!
  []
  (->>
   (git/sh! dir "gc" "--aggressive")
   (timers/time! gc-timer)))

(def fetch-timer
  (timers/timer ["git" "remote" "fetch-timer"]))

(defn fetch!
  []
  (when origin
    (->>
     (git/sh! dir "fetch" "--quiet" "origin" "--tags")
     (timers/time! fetch-timer))))

(def push-timer
  (timers/timer ["git" "remote" "push-timer"]))

(defn push!
  []
  (when origin
    (->>
     (git/sh! dir "push" "--quiet" "origin" branch)
     (timers/time! push-timer))))

(defstate ^{:on-reload :noop} repo
  :start (do
           (log/info (-> (git/sh! dir "--version") :out str/trim))
           (with-lock
             (fn []
               (let [f    (file dir)
                     path (path dir)]
                 (when-not (.isDirectory (file f ".git"))
                   (if origin
                     (do
                       (log/info {:git {:clone origin}})
                       (git/sh! dir "clone" "--quiet" origin path))
                     (do
                       (log/info {:git {:init path}})
                       (.mkdirs f)
                       (git/sh! dir "init" "--quiet" path))))
                 (when-not (= branch (git/head-ref dir))
                   (if origin
                     (git/sh! dir "checkout" "--track" (str "origin/" branch))
                     (git/sh! dir "checkout" "-b" branch)))
                 (log/info {:git {:repo path :branch branch :origin origin}}))))))

(def status-timer
  (timers/timer ["git" "local" "status-timer"]))

(defn- status
  []
  (->>
   (git/status dir)
   (timers/time! status-timer)))

(defn add!
  [f]
  (with-lock (fn [] (git/sh! dir "add" (path f)))))

(def commit-timer
  (timers/timer ["git" "local" "commit-timer"]))

(defn commit!
  []
  (with-lock
    (fn []
      (when-let [changes (not-empty (status))]
        (->>
         (git/sh! dir "commit" "-a" "-m" "zdl-lex-server")
         (timers/time! commit-timer))
        (publish-changes! (mapcat :paths changes))
        (push!)))))

(defn fast-forward!
  [ref]
  (fetch!)
  (with-lock
    (fn []
      (let [head (git/head-rev dir)]
        (git/assert-clean dir)
        (git/sh! dir "merge" "--ff-only" "-q" ref)
        (let [diff (git/sh! dir "diff" "--numstat" (str head ".." "HEAD"))]
          (publish-changes!
           (sequence
            (comp
             (map not-empty) (remove nil?)
             (map #(str/split % #"\t"))
             (map #(nth % 2)))
            (str/split-lines (get diff :out))))))))
  (push!))

(defstate scheduled-commit
  :start (cron/schedule "0 */15 * * * ?" "Git commit" commit!)
  :stop (a/close! scheduled-commit))

(defstate scheduled-gc
  :start (cron/schedule "0 0 5 * * ?" "Git Garbage Collection" gc!)
  :stop (a/close! scheduled-gc))

(defn get-file
  [path]
  (when-let [f (file dir path)] f))

(defn edit!
  ([path editor]
   (edit! default-timeout path editor))
  ([lock-timeout path editor]
  (with-lock
    lock-timeout
    (fn []
      (let [f       (file dir path)
            exists? (file? f)]
        (when-not exists? (.. f (getParentFile) (mkdirs)))
        (try+
         (editor f)
         (when-not exists? (add! f))
         (server.article/update! [f])
         (catch [:type ::unmodified] _))
        f)))))
