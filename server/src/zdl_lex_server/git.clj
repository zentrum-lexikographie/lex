(ns zdl-lex-server.git
  (:require [clj-jgit.internal :as jgit-int]
            [clj-jgit.porcelain :as jgit]
            [clj-jgit.querying :as jgit-query]
            [clojure.core.async :as a]
            [clojure.java.shell :as sh]
            [clojure.set :refer [union]]
            [clojure.spec.alpha :as s]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [metrics.timers :refer [deftimer time!]]
            [ring.util.http-response :as htstatus]
            [clojure.tools.logging :as log]
            [zdl-lex-common.bus :as bus]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.util :refer [->clean-map file]]
            [zdl-lex-server.lock :as lock]
            [clojure.string :as str]))

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

(deftimer [git local gc-timer])

(defn git-gc
  []
  (->>
   (git "gc" "--aggressive")
   (time! gc-timer)))

(defn git-clone
  []
  (let [origin (env :git-origin)]
    (log/info {:git {:clone origin}})
    (git "clone" "--quiet" origin (str dir))))

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

(defn git-load
  []
  (log/info {:git {:load (str dir)}})
  (jgit/load-repo (str dir)))

(defn git-checkout
  [repo]
  (when-not (= branch (jgit/git-branch-current repo))
    (when-not (some #{branch} (jgit/git-branch-list repo))
      (jgit/git-branch-create repo branch))
    (log/info {:git {:checkout branch}})
    (jgit/git-checkout repo :name branch)))

(defstate ^{:on-reload :noop} repo
  :start (lock/with-global-write-lock
           (when-not (fs/directory? (file dir ".git"))
             (git-clone))
           (let [repo (git-load)]
             (git-checkout repo)
             (log/info {:git {:repo (str dir) :branch branch}})
             repo)))

(defn- git-path
  "Path relative to the git directory."
  [^java.io.File f]
  (str (.. (.toPath dir) (relativize (.toPath f)))))

(defn- classify-changes [changes]
  (let [files (map (partial file dir) changes)
        modified (filter fs/exists? files)
        deleted (remove fs/exists? files)]
    {:files
     {:modified (vec modified)
      :deleted (vec deleted)}
     :git
     {:modified (vec (map git-path modified))
      :deleted (vec (map git-path deleted))}}))

(defn- send-changes [changes]
  (->> (changes :files)
       (log/spy :trace)
       (bus/publish! :git-changes))
  changes)

(deftimer [git local status-timer])

(defn- git-status []
  (->> (jgit/git-status repo)
       (vals)
       (apply union)
       (classify-changes)
       (time! status-timer)))

(defn- git-changed? [changes]
  (not (every? empty? (-> changes :git vals))))

(def committer
  {:name (env :git-commit-user)
   :email (env :git-commit-email)})

(deftimer [git local commit-timer])

(defn commit []
  (lock/with-global-write-lock
    (let [changes (git-status)]
      (when (git-changed? changes)
        (when-let [modified (not-empty (get-in changes [:git :modified]))]
          (jgit/git-add repo modified))
        (when-let [deleted (not-empty (get-in changes [:git :deleted]))]
          (jgit/git-rm repo deleted))
        (->>
         (jgit/git-commit repo "zdl-lex-server" :committer committer)
         (time! commit-timer))
        (send-changes changes)
        ;; Changes will be propagated, even if pushing to the remote fails
        (try (git-push) (catch Throwable t)))
      (changes :git))))

(defn fast-forward [refs]
  (lock/with-global-write-lock
    (when (git-changed? (git-status))
      (throw (IllegalStateException. "Uncommitted changes in server's working dir")))
    (git-fetch)
    (with-open [rev-walk (jgit-int/new-rev-walk repo)]
      (if-let [head (jgit-query/find-rev-commit repo rev-walk "HEAD")]
        (let [merge (jgit/git-merge repo refs :ff-mode :ff-only)]
          (if (.. merge (getMergeStatus) (isSuccessful))
            (do (git-push)
                (->> (jgit/git-log repo :since head)
                     (map :id) (reverse)
                     (mapcat (partial jgit-query/changed-files repo))
                     (map first)
                     (classify-changes)
                     (send-changes)
                     (:git)))
            (throw (ex-info "Error fast-forwarding git branch"
                            {:head head :refs refs :merge merge}))))
        (throw (ex-info "Error fast-forwarding git branch: No HEAD found"))))))

(defstate commit-scheduler
  :start (cron/schedule "0 * * * * ?" "Git commit" commit)
  :stop (a/close! commit-scheduler))

(defn handle-commit [req]
  (htstatus/ok (commit)))

(defn handle-fast-forward [req]
  (if-let [refs (-> req :parameters :path :refs not-empty)]
    (try
      (htstatus/ok (fast-forward refs))
      (catch Throwable t
        (log/warn t)
        (htstatus/bad-request)))
    (htstatus/bad-request)))

(s/def ::refs string?)
(s/def ::fast-forward-cmd (s/keys :req-un [::refs]))

(def ring-handlers
  ["/git"
   [""
    {:patch {:summary "Commit pending changes on the server's branch"
             :tags ["Article" "Git" "Admin"]
             :handler handle-commit}}]
   ["/ff/*refs"
    {:post {:summary "Fast-forwards the server's branch to the given refs"
            :tags ["Article" "Git" "Admin"]
            :parameters {:path ::fast-forward-cmd}
            :handler handle-fast-forward}}]])
