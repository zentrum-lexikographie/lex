(ns zdl.lex.server.git
  (:require [clojure.core.async :as a]
            [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [metrics.timers :refer [deftimer time!]]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [zdl.lex.bus :as bus]
            [zdl.lex.cron :as cron]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :as fs]
            [zdl.lex.util :refer [file fpath relativize]])
  (:import java.io.File))

(def dir
  (delay (fs/data-dir "git")))

(def origin
  (delay (getenv "ZDL_LEX_GIT_ORIGIN")))

(def branch
  (delay (getenv "ZDL_LEX_GIT_BRANCH" "zdl-lex-server/development")))

(defn file->id
  [f]
  (str (relativize @dir f)))

(defn git! [& args]
  (->>
   (let [result (apply sh/sh (concat ["git"] args))
         succeeded? (= (result :exit) 0)]
     (log/debug {:git! args})
     (when-not succeeded? (throw (ex-info (str args) result)))
     result)
   (sh/with-sh-dir @dir)))

(defstate git-cmd
  :start (log/info {:git (-> (git! "--version") :out str/trim)}))

(deftimer [git local gc-timer])

(defn git-gc
  []
  (->>
   (git! "gc" "--aggressive")
   (time! gc-timer)))

(defstate gc-scheduler
  :start (cron/schedule "0 0 5 * * ?" "Git GC" git-gc)
  :stop (a/close! gc-scheduler))

(deftimer [git remote fetch-timer])

(defn git-fetch
  []
  (when @origin
    (->>
     (git! "fetch" "--quiet" "origin" "--tags")
     (time! fetch-timer))))

(deftimer [git remote push-timer])

(defn git-push
  []
  (when @origin
    (->>
     (git! "push" "--quiet" "origin" @branch)
     (time! push-timer))))

(defn git-refs
  []
  (->> (git! "for-each-ref" "--format" "%(refname:short)")
       :out str/split-lines
       (apply sorted-set)))

(defn git-ref
  []
  (->> (git! "symbolic-ref" "--short" "-q" "HEAD") :out str/trim))

(defn git-rev
  []
  (->> (git! "rev-parse" "HEAD") :out str/trim))

(defstate ^{:on-reload :noop} repo
  :start (locking @dir
           (let [dir @dir
                 path (fpath dir)
                 branch @branch
                 origin @origin]
             (when-not (.isDirectory (file dir ".git"))
               (if origin
                 (do
                   (log/info {:git {:clone origin}})
                   (git! "clone" "--quiet" origin path))
                 (do
                   (log/info {:git {:init path}})
                   (git! "init" "--quiet" dir))))
             (when-not (= branch (git-ref))
               (if origin
                 (git! "checkout" "--track" (str "origin/" branch))
                 (git! "checkout" "-b" branch)))
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

(def status->kw
  {\space :ok
   \A :added
   \C :copied
   \D :deleted
   \M :modified
   \R :renamed
   \? :untracked
   \! :ignored})

(defn status->paths
  [path]
  (->> (str/split path #"->")
       (map #(str/replace % #"\"" ""))
       (map not-empty) (remove nil?)))

(defn- git-status []
  (->> (git! "status" "-s" "--porcelain")
       :out str/split-lines
       (map not-empty) (remove nil?)
       (map #(array-map :index (status->kw (nth % 0))
                        :dir (status->kw (nth % 1))
                        :paths (status->paths (subs % 3))))
       (time! status-timer)))

(deftimer [git local commit-timer])

(defn git-add
  [f]
  (locking @dir
    (git! "add" (fpath f))))

(defn commit
  []
  (when
      (->>
       (locking @dir
         (when-let [changes (not-empty (git-status))]
           (->> (git! "commit" "-a" "-m" "zdl-lex-server")
                (time! commit-timer))
           changes))
       (mapcat :paths)
       (publish-paths))
    (git-push)))

(defn fast-forward [ref]
  (git-fetch)
  (locking @dir
    (when (not-empty (git-status))
      (throw
       (IllegalStateException. "Uncommitted changes in server's working dir")))
    (let [head (git-rev)]
      (git! "merge" "--ff-only" "-q" ref)
      (->> (git! "diff" "--numstat" (str head ".." "HEAD"))
           :out str/split-lines
           (map not-empty) (remove nil?)
           (map #(str/split % #"\t"))
           (map #(nth % 2))
           (publish-paths))))
  (git-push))

(defstate commit-scheduler
  :start (cron/schedule "0 */15 * * * ?" "Git commit" commit)
  :stop (a/close! commit-scheduler))

(defn handle-commit [req]
  (htstatus/ok (commit)))

(defn handle-fast-forward [req]
  (if-let [ref (-> req :parameters :path :ref)]
    (try
      (htstatus/ok (fast-forward ref))
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
