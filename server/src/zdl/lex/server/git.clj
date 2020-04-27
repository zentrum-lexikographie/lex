(ns zdl.lex.server.git
  (:require [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [metrics.timers :refer [deftimer time!]]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [zdl.lex.bus :as bus]
            [zdl.lex.cron :as cron]
            [zdl.lex.data :as data]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :refer [file path relativize]]
            [zdl.lex.git :as git])
  (:import java.io.File))

(def dir
  (delay (data/dir "git")))

(def origin
  (delay (getenv "ZDL_LEX_GIT_ORIGIN")))

(def branch
  (delay (getenv "ZDL_LEX_GIT_BRANCH" "zdl-lex-server/development")))

(defn file->id
  [f]
  (str (relativize @dir f)))

(defstate git-available?
  :start (log/info (-> (git/sh! @dir "--version") :out str/trim)))

(deftimer [git local gc-timer])

(defn gc!
  []
  (->>
   (git/sh! @dir "gc" "--aggressive")
   (time! gc-timer)))

(defstate gc-scheduler
  :start (cron/schedule "0 0 5 * * ?" "Git GC" gc!)
  :stop (a/close! gc-scheduler))

(deftimer [git remote fetch-timer])

(defn fetch!
  []
  (when @origin
    (->>
     (git/sh! @dir "fetch" "--quiet" "origin" "--tags")
     (time! fetch-timer))))

(deftimer [git remote push-timer])

(defn push!
  []
  (when @origin
    (->>
     (git/sh! @dir "push" "--quiet" "origin" @branch)
     (time! push-timer))))

(defstate ^{:on-reload :noop} repo
  :start (locking @dir
           (let [dir @dir
                 f (file dir)
                 path (path dir)
                 branch @branch
                 origin @origin]
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
             (log/info {:git {:repo path :branch branch}}))))

(defn publish-changes
  [files]
  (when (seq files)
    (->> {:modified (vec (filter #(.exists ^File %) files))
          :deleted (vec (remove #(.exists ^File %) files))}
         (bus/publish! [:git-changes]))
    files))

(defn publish-paths
  [paths]
  (publish-changes (map (partial file @dir) paths)))

(deftimer [git local status-timer])

(defn- status
  []
  (->> (git/status @dir)
       (time! status-timer)))

(deftimer [git local commit-timer])

(defn add!
  [f]
  (locking @dir
    (git/sh! @dir "add" (path f))))

(defn commit!
  []
  (when
      (->>
       (locking @dir
         (when-let [changes (not-empty (status))]
           (->> (git/sh! @dir "commit" "-a" "-m" "zdl-lex-server")
                (time! commit-timer))
           changes))
       (mapcat :paths)
       (publish-paths))
    (push!)))

(defn fast-forward!
  [ref]
  (fetch!)
  (locking @dir
    (let [dir @dir
          head (git/head-rev dir)]
      (git/assert-clean dir)
      (git/sh! dir "merge" "--ff-only" "-q" ref)
      (->> (git/sh! dir "diff" "--numstat" (str head ".." "HEAD"))
           :out str/split-lines
           (map not-empty) (remove nil?)
           (map #(str/split % #"\t"))
           (map #(nth % 2))
           (publish-paths))))
  (push!))

(defstate commit-scheduler
  :start (cron/schedule "0 */15 * * * ?" "Git commit" commit!)
  :stop (a/close! commit-scheduler))

(defn handle-commit [req]
  (htstatus/ok (commit!)))

(defn handle-fast-forward [req]
  (if-let [ref (-> req :parameters :path :ref)]
    (try
      (htstatus/ok (fast-forward! ref))
      (catch Throwable t
        (log/warn t)
        (htstatus/bad-request)))
    (htstatus/bad-request)))

(s/def ::ref string?)
(s/def ::fast-forward-cmd (s/keys :req-un [::ref]))

(def ring-handlers
  ["/git"
   [""
    {:patch {:summary "Commit pending changes on the server's branch"
             :tags ["Article" "Git" "Admin"]
             :handler handle-commit}}]
   ["/ff/:ref"
    {:post {:summary "Fast-forwards the server's branch to the given refs"
            :tags ["Article" "Git" "Admin"]
            :parameters {:path ::fast-forward-cmd}
            :handler handle-fast-forward}}]])
