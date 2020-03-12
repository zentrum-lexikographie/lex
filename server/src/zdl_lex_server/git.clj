(ns zdl-lex-server.git
  (:require [clojure.core.async :as a]
            [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [metrics.timers :refer [deftimer time!]]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [zdl-lex-common.bus :as bus]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.util :refer [file]]))

(def dir (file (env :data-dir) "git"))

(def branch (env :git-branch))

(def cmd-env
  (->>
   (let [{:keys [git-auth-key-dir git-auth-key-name]} env]
     (concat
      ["ssh" "-o" "StrictHostKeyChecking=no"]
      (if (and git-auth-key-dir git-auth-key-name)
        ["-i" (str (file git-auth-key-dir git-auth-key-name))])))
   (str/join " ")
   (array-map "GIT_SSH_COMMAND")
   (merge (into {} (System/getenv)))))

(defn git [& args]
  (->>
   (let [result (apply sh/sh (concat ["git"] args))
         succeeded? (= (result :exit) 0)]
     (log/log (if succeeded? :debug :warn) {:git args :result result})
     (when-not succeeded? (throw (ex-info (str args) result)))
     result)
   (sh/with-sh-env cmd-env)
   (sh/with-sh-dir dir)))

(defstate git-cmd
  :start (do
           (when-not (fs/exists? dir)
             (fs/mkdirs dir))
           (let [{:keys [out]} (git "--version")]
             (log/info {:git (str/trim out)}))))

(defn git-clone
  []
  (let [origin (env :git-origin)]
    (log/info {:git {:clone origin}})
    (git "clone" "--quiet" origin (str dir))))

(deftimer [git local gc-timer])

(defn git-gc
  []
  (->>
   (git "gc" "--aggressive")
   (time! gc-timer)))

(defstate gc-scheduler
  :start (cron/schedule "0 0 5 * * ?" "Git GC" git-gc)
  :stop (a/close! gc-scheduler))

(deftimer [git remote fetch-timer])

(defn git-fetch
  []
  (->>
   (git "fetch" "--quiet" "origin" "--tags")
   (time! fetch-timer)))

(deftimer [git remote push-timer])

(defn git-push
  []
  (->>
   (git "push" "--quiet" "origin" branch)
   (time! push-timer)))

(defn git-refs
  []
  (->> (git "for-each-ref" "--format" "%(refname:short)")
       :out str/split-lines
       (apply sorted-set)))

(defn git-ref
  []
  (->> (git "symbolic-ref" "--short" "-q" "HEAD") :out str/trim))

(defn git-rev
  []
  (->> (git "rev-parse" "HEAD") :out str/trim))

(defn git-status
  []
  (let [{:keys [out]} (git "status" "-b" "-s" "--porcelain")
        lines (str/split-lines out)
        lines (->> lines (map not-empty) (remove nil?))
        states (map #(hash-map :type (subs % 0 2) :id (subs % 3)) lines)]
    states))

(defstate ^{:on-reload :noop} repo
  :start (locking dir
           (when-not (fs/directory? (file dir ".git"))
             (git-clone))
           (when-not (#{branch} (git-ref))
             (git "checkout" "--track" (str "origin/" branch)))
           (log/info {:git {:repo (str dir) :branch branch}})))

(defn publish-changes
  [files]
  (when (seq files)
    (bus/publish!
     :git-changes
     {:modified (vec (filter fs/exists? files))
      :deleted (vec (remove fs/exists? files))})
    files))

(def publish-paths
  (comp publish-changes (partial map (partial file dir))))

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
  (->> (git "status" "-s" "--porcelain")
       :out str/split-lines
       (map not-empty) (remove nil?)
       (map #(array-map :index (status->kw (nth % 0))
                        :dir (status->kw (nth % 1))
                        :paths (status->paths (subs % 3))))
       (time! status-timer)))

(deftimer [git local commit-timer])

(def commit-author-arg
  (format "--author=%s <%s>" (env :git-commit-user) (env :git-commit-email)))

(defn commit []
  (when
      (->>
       (locking dir
         (when-let [changes (not-empty (git-status))]
           (->> (git "commit" commit-author-arg "-a" "-m" "zdl-lex-server")
                (time! commit-timer))
           changes))
       (mapcat :paths)
       (publish-paths))
    (git-push)))

(defn fast-forward [ref]
  (git-fetch)
  (locking dir
    (when (not-empty (git-status))
      (throw
       (IllegalStateException. "Uncommitted changes in server's working dir")))
    (let [head (git-rev)]
      (git "merge" "--ff-only" "-q" ref)
      (->> (git "diff" "--numstat" (str "3437d55d4c528" ".." "HEAD"))
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
