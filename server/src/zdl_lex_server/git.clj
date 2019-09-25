(ns zdl-lex-server.git
  (:require [clj-jgit.porcelain :as jgit]
            [clj-jgit.querying :as jgit-query]
            [clojure.core.async :as a]
            [clojure.set :refer [union]]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.bus :as bus]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-server.store :as store]
            [ring.util.http-response :as htstatus]))

(let [absolute-path #(->> % (fs/file store/git-dir) fs/absolute fs/normalized)]
  (defn- send-changes [changed-files]
    (when-let [changeset (some->> changed-files
                                  (map absolute-path)
                                  (into (sorted-set)))]
      (timbre/spy :trace changeset)
      (bus/publish! :git-changes changeset)
      changed-files)))

(defn commit []
  (store/with-write-lock
    (jgit/with-repo store/git-dir
      (let [changed-files (->> (jgit/git-status repo) (vals) (apply union))]
        (when-not (empty? changed-files)
          (jgit/with-repo store/git-dir
            (jgit/git-add repo ".")
            (jgit/git-commit repo "zdl-lex-server")
            (jgit/git-push repo))
          (send-changes changed-files))))))

(let [all-refs ["refs/tags/*:refs/tags/*" "refs/heads/*:refs/remotes/origin/*"]]
  (defn fast-forward [refs]
    (store/with-write-lock
      (jgit/with-repo store/git-dir
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

(defn handle-fast-forward
  [{{:keys [ref]} :path-params}]
  (if (empty? ref)
    (htstatus/bad-request {:ref ref})
    (try 
      (htstatus/ok (fast-forward ref))
      (catch Throwable t
        (timbre/warn t)
        (htstatus/bad-request (ex-data t))))))

(def ring-handlers
  ["/git"
   ["/*ref" {:post handle-fast-forward}]])
