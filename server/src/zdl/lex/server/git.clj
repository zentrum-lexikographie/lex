(ns zdl.lex.server.git
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [manifold.bus :as bus]
            [metrics.timers :refer [deftimer time!]]
            [mount.core :refer [defstate]]
            [zdl.lex.data :as data]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :refer [file path]]
            [zdl.lex.git :as git])
  (:import java.io.File
           java.util.concurrent.locks.ReentrantReadWriteLock
           java.util.concurrent.Semaphore))

(def dir
  (data/dir "git"))

(def origin
  (getenv "GIT_ORIGIN"))

(def branch
  (getenv "GIT_BRANCH" "zdl-lex-server/development"))

(def lock
  (Semaphore. 1))

(defn lock!
  []
  (.acquire lock))

(defn unlock!
  []
  (.release lock))

(defmacro with-lock
  [& body]
  `(try
     (lock!)
     ~@body
     (finally
       (unlock!))))

(def events
  (bus/event-bus))

(defn publish-changes!
  [paths]
  (when (seq paths)
    (let [files   (map #(file dir %) paths)
          updated (filter #(.exists ^File %) files)
          removed (remove #(.exists ^File %) files)]
      (when (seq updated)
        (bus/publish! events :updated updated))
      (when (seq removed)
        (bus/publish! events :removed updated))
      files)))

(deftimer [git local gc-timer])

(defn gc!
  []
  (time! gc-timer (git/sh! dir "gc" "--aggressive")))

(deftimer [git remote fetch-timer])

(defn fetch!
  []
  (when origin
    (time! fetch-timer (git/sh! dir "fetch" "--quiet" "origin" "--tags"))))

(deftimer [git remote push-timer])

(defn push!
  []
  (when origin
    (time! push-timer (git/sh! dir "push" "--quiet" "origin" branch))))

(defstate ^{:on-reload :noop} repo
  :start (with-lock
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
             (log/info {:git {:repo path :branch branch :origin origin}}))))

(defstate git-available?
  :start (log/info (-> (git/sh! dir "--version") :out str/trim)))

(deftimer [git local status-timer])

(defn- status
  []
  (->> (git/status dir)
       (time! status-timer)))

(defn add!
  [f & {:keys [lock?] :or {lock? true}}]
  (if lock?
    (with-lock (git/sh! dir "add" (path f)))
    (git/sh! dir "add" (path f))))

(deftimer [git local commit-timer])

(defn commit!
  []
  (with-lock
    (when-let [changes (not-empty (status))]
      (time! commit-timer (git/sh! dir "commit" "-a" "-m" "zdl-lex-server"))
      (publish-changes! (mapcat :paths changes))
      (push!))))

(defn fast-forward!
  [ref]
  (fetch!)
  (with-lock
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
          (str/split-lines (get diff :out)))))))
  (push!))
