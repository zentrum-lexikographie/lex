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

(defn- send-changes [changed-files]
  (when-let [changeset (some->> changed-files
                                (map (partial file git-dir))
                                (into (sorted-set)))]
    (timbre/spy :trace changeset)
    (bus/publish! :git-changes changeset)
    changed-files))

(defn commit []
  (lock/with-global-write-lock
    (jgit/with-repo git-dir
      (let [changed-files (->> (jgit/git-status repo) (vals) (apply union))]
        (when-not (empty? changed-files)
          (jgit/git-add repo ".")
          (jgit/git-commit repo "zdl-lex-server")
          (jgit/git-push repo)
          (send-changes changed-files))))))

(let [all-refs ["refs/tags/*:refs/tags/*" "refs/heads/*:refs/remotes/origin/*"]]
  (defn fast-forward [refs]
    (lock/with-global-write-lock
      (jgit/with-repo git-dir
        (jgit/git-fetch repo :ref-specs all-refs)
        (when-let [head (jgit-query/find-rev-commit repo rev-walk "HEAD")]
          (let [merge (jgit/git-merge repo refs :ff-mode :ff-only)]
            (if (.. merge (getMergeStatus) (isSuccessful))
              (do (jgit/git-push repo)
                  (->> (jgit/git-log repo :since head)
                       (map :id) (reverse)
                       (mapcat (partial jgit-query/changed-files repo))
                       (map first)
                       (send-changes)))
              (throw (ex-info "Error fast-forwarding git branch"
                              {:head head :refs refs :merge merge})))))))))

(defstate commit-scheduler
  :start (cron/schedule "0 * * * * ?" "Git commit" commit)
  :stop (a/close! commit-scheduler))

(defn handle-fast-forward [req]
  (let [refs (-> req :parameters :path :refs)]
    (timbre/warn refs)
    (if (empty? refs)
      (htstatus/bad-request {:ref refs})
      (try
        (htstatus/ok (fast-forward refs))
        (catch Throwable t
          (timbre/warn t)
          (htstatus/bad-request (ex-data t)))))))
  
(s/def ::refs string?)
(s/def ::fast-forward-cmd (s/keys :req-un [::refs]))

(def ring-handlers
  ["/git"
   ["/*refs" {:post {:summary "Fast-forwards the article master branch to the given refs"
                     :tags ["Article" "Git" "Admin"]
                     :parameters {:path ::fast-forward-cmd}
                     :handler handle-fast-forward}}]])
