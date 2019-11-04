(ns zdl-lex-server.git
  (:require [clj-jgit.porcelain :as jgit]
            [clj-jgit.querying :as jgit-query]
            [clojure.core.async :as a]
            [clojure.set :refer [union]]
            [clojure.spec.alpha :as s]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.bus :as bus]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.util :refer [file]]
            [zdl-lex-server.auth :as auth]
            [zdl-lex-server.lock :as lock]))

(defstate ^{:on-reload :noop} git-dir 
  :start (lock/with-global-write-lock
           (let [git-dir (file (env :data-dir) "git")]
             (when-not (fs/directory? (file git-dir ".git"))
               (fs/mkdirs git-dir)
               (jgit/git-init :dir git-dir)
               (jgit/with-repo git-dir
                 (fs/mkdirs (file git-dir "articles"))
                 (jgit/git-add repo ".")
                 (jgit/git-commit repo "Init")))
             git-dir)))

(defstate ^{:on-reload :noop} articles-dir
  :start (fs/file git-dir "articles"))

(defn- git-path
  "Path relative to the git directory."
  [^java.io.File f]
  (str (.. (.toPath git-dir) (relativize (.toPath f)))))

(defn- classify-changes [changes]
  (let [files (map (partial file git-dir) changes)
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
       (timbre/spy :trace)
       (bus/publish! :git-changes))
  changes)

(defn- git-status [repo]
  (->> (jgit/git-status repo) (vals) (apply union) (classify-changes)))

(defn- git-changed? [changes]
  (not (every? empty? (-> changes :git vals))))

(def branch
  "The branch we operate on."
  "zdl-lex-server")

(defn commit []
  (lock/with-global-write-lock
    (jgit/with-repo git-dir
      (when-not (= branch (jgit/git-branch-current repo))
        (jgit/git-checkout repo :name branch :create-branch? true))
      (let [changes (git-status repo)]
        (when (git-changed? changes)
          (when-let [modified (not-empty (get-in changes [:git :modified]))]
            (jgit/git-add repo modified))
          (when-let [deleted (not-empty (get-in changes [:git :deleted]))]
            (jgit/git-rm repo deleted))
          (jgit/git-commit repo "zdl-lex-server")
          (jgit/git-push repo :refs branch)
          (send-changes changes))
        (changes :git)))))

(def ^:private all-refs
  ["refs/tags/*:refs/tags/*" "refs/heads/*:refs/remotes/origin/*"])

(defn fast-forward [refs]
  (lock/with-global-write-lock
    (jgit/with-repo git-dir
      (when (->> repo git-status git-changed?)
        (throw (IllegalStateException. "Uncommitted changes in server's working dir")))
      (jgit/git-fetch repo :ref-specs all-refs)
      (if-let [head (jgit-query/find-rev-commit repo rev-walk "HEAD")]
        (let [merge (jgit/git-merge repo refs :ff-mode :ff-only)]
          (if (.. merge (getMergeStatus) (isSuccessful))
            (do (jgit/git-push repo)
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
        (timbre/warn t)
        (htstatus/bad-request (ex-data t))))
    (htstatus/bad-request)))

(s/def ::refs string?)
(s/def ::fast-forward-cmd (s/keys :req-un [::refs]))

(def ring-handlers
  ["/git"
   ["" {:patch {:summary "Commit pending changes on the server's branch"
                :tags ["Article" "Git" "Admin"]
                :handler handle-commit
                :middleware [auth/wrap-admin-only]}}]
   ["/ff/*refs" {:post {:summary "Fast-forwards the server's branch to the given refs"
                        :tags ["Article" "Git" "Admin"]
                        :parameters {:path ::fast-forward-cmd}
                        :handler handle-fast-forward
                        :middleware [auth/wrap-admin-only]}}]])
