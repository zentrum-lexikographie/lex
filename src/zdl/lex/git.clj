(ns zdl.lex.git
  (:require
   [babashka.fs :as fs]
   [clojure.core.async :as a]
   [clojure.java.io :as io]
   [clojure.java.process :as p]
   [clojure.string :as str]
   [ring.util.response :as resp]
   [taoensso.telemere :as tel]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.metrics :as metrics])
  (:import
   (java.util.concurrent TimeUnit)
   (java.util.concurrent.locks ReentrantLock)))

(def ^:dynamic *dir*
  (getenv "GIT_DIR" "wb"))

(def ^:dynamic *origin*
  (getenv "GIT_ORIGIN"))

(def ^:dynamic *branch*
  (getenv "GIT_BRANCH" "dev"))

(def ^:dynamic *user*
  (getenv "GIT_USER"))

(def ^:dynamic *password*
  (getenv "GIT_PASSWORD"))

(def ^:dynamic *author-name*
  (getenv "GIT_AUTHOR_NAME" "ZDL-Lex"))

(def ^:dynamic *author-email*
  (getenv "GIT_AUTHOR_EMAIL" "noreply@dwds.de"))

(def ^:dynamic *push?*
  (getenv "GIT_PUSH"))

(defn xml-file?
  [f]
  (let [^String name (fs/file-name f)]
    (and (. name (endsWith ".xml")) (not (. name (startsWith "."))))))

(defn xml-files
  []
  (->> (file-seq (fs/file *dir*)) (filter xml-file?)))

(defn xml-paths
  []
  (map #(str (fs/relativize *dir* %)) (xml-files)))

(def changed-paths-ch
  (a/chan 16))

(def changed-paths-mult
  (a/mult changed-paths-ch))

(def ^ReentrantLock lock
  (ReentrantLock.))

(def lock-timeout
  30000)

(defmacro with-git
  [& forms]
  `(do
     (when-not (.tryLock lock lock-timeout TimeUnit/MILLISECONDS)
       (throw (ex-info "Timeout" {:type     ::lock-timeout
                                  ::timeout lock-timeout})))
     (try ~@forms (finally (.unlock lock)))))

(defn git!
  [& args]
  (tel/with-ctx+ {::dir *dir* ::args args}
    (try
      (let [opts {:dir *dir*
                  :env {"GIT_AUTHOR_NAME"     *author-name*
                        "GIT_AUTHOR_EMAIL"    *author-email*
                        "GIT_COMMITTER_NAME"  *author-name*
                        "GIT_COMMITTER_EMAIL" *author-email*}}
            out  (-> (apply p/exec (concat (list opts "git") (map str args)))
                     (str/trim) (not-empty))]
        (tel/with-ctx+ {::out out} (tel/event! ::git :debug) out))
      (catch Throwable t
        (throw (tel/error! ::git t))))))

(def gc-timer
  (metrics/timer "git.gc"))

(defn gc!
  []
  (with-open [_ (metrics/timed! gc-timer)]
    (git! "gc" "--aggressive")))

(def fetch-timer
  (metrics/timer "git.fetch"))

(defn fetch!
  []
  (when *origin*
    (with-open [_ (metrics/timed! fetch-timer)]
      (git! "fetch" "--quiet" "origin" "--tags"))))

(def push-timer
  (metrics/timer "git.push"))

(defn push!
  []
  (when (and *origin* *push?*)
    (with-open [_ (metrics/timed! push-timer)]
      (git! "push" "--quiet" "origin" *branch*))))

(defn add!
  [f]
  (with-git (git! "add" (fs/path f))))

(defn rm!
  [f]
  (with-git (git! "rm" (fs/path f))))

(defn status->paths
  [status-line]
  (->> (str/split (subs status-line 3) #"->")
       (map #(str/replace % #"\"" ""))
       (map not-empty) (remove nil?)))

(def status-timer
  (metrics/timer "git.status"))

(defn changed-paths
  []
  (with-open [_ (metrics/timed! status-timer)]
    (some->> (git! "status" "-s" "--porcelain")
             str/split-lines
             (into [] (comp (map not-empty)
                            (remove nil?)
                            (mapcat status->paths))))))

(defn dirty?
  []
  (seq (changed-paths)))

(defn assert-clean
  []
  (when (dirty?) (throw (IllegalStateException. "Git dir is dirty."))))

(def commit-timer
  (metrics/timer "git.commit"))

(defn commit!
  []
  (and (some->> (with-git
                  (let [paths (changed-paths)]
                    (when (seq paths)
                      (with-open [_ (metrics/timed! commit-timer)]
                        (git! "commit" "-a" "-m" "zdl-lex-server"))
                      paths)))
                (a/>!! changed-paths-ch))
       (push!)))

(defn sync!
  []
  (fetch!)
  (commit!))

(defn init-repo!
  []
  (when-not (fs/directory? *dir* ".git")
    (tel/event! ::init :info)
    (fs/create-dirs *dir*)
    (git! "init" "--quiet")
    (when (and *origin* *user* *password*)
      (git! "config" "credential.helper"
            (format
             "!f() { echo 'username=%s'; echo 'password=%s'; }; f"
             *user* *password*))
      (git! "remote" "add" "origin" *origin*)
      (fetch!))))

(defn checkout-branch!
  []
  (when-not (= *branch* (git! "symbolic-ref" "--short" "-q" "HEAD"))
    (tel/event! ::checkout :info)
    (if *origin*
      (git! "checkout" "--track" (str "origin/" *branch*))
      (git! "checkout" "-b" *branch*))))

(defn init!
  []
  (with-git
    (tel/with-ctx+ {::dir *dir* ::origin *origin* ::branch *branch*}
      (let [init?     (init-repo!)
            checkout? (checkout-branch!)]
        (or init? checkout?)))))

(defn get-article-file
  [path]
  (let [f (fs/file *dir* path)] (when (fs/regular-file? f) f)))

(defn write-article-file
  [path write-fn]
  (let [f (fs/file *dir* path)]
    (with-git
      (let [exists? (fs/regular-file? f)]
        (when-not exists? (-> f fs/parent fs/create-dirs))
        (with-open [output (io/output-stream f)] (write-fn output))
        (when-not exists? (add! path))))
    (a/>!! changed-paths-ch (list path))
    f))

(defn head-rev
  []
  (git! "rev-parse" "HEAD"))

(defn diff-changed-paths
  [prev-head]
  (some->> (git! "diff" "--numstat" (str prev-head ".." "HEAD"))
           (str/split-lines)
           (into [] (comp (map not-empty) (remove nil?)
                          (map #(str/split % #"\t"))
                          (map #(nth % 2))))))

(defn handle-fast-forward
  [{{{:keys [ref]} :path} :parameters}]
  (try
    (fetch!)
    (with-git
      (let [prev-head (head-rev)]
        (assert-clean)
        (git! "merge" "--ff-only" "-q" ref)
        (a/>!! changed-paths-ch (diff-changed-paths prev-head))))
    (push!)
    (resp/response {:ff ref})
    (catch Throwable t
      (tel/error! {:error t :level :warn})
      (-> ref (resp/response) (resp/status 400)))))

(defn handle-rebase
  [{{{:keys [ref]} :path} :parameters}]
  (with-git
    (sync!)
    (let [prev-head (head-rev)]
      (try
        (git! "rebase" ref)
        (a/>!! changed-paths-ch (diff-changed-paths prev-head))
        (push!)
        (resp/response {:ff ref})
        (catch Throwable t
          (tel/error! {:error t :level :warn})
          (git! "rebase" "--abort")
          (-> {:ff ref} (resp/response) (resp/status 400)))))))
