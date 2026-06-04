(ns zdl.lex.git
  (:require
   [babashka.fs :as fs]
   [clojure.core.async :as a]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh :refer [sh]]
   [clojure.string :as str]
   [integrant.core :as ig]
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

(defn article-file?
  [f]
  (let [name (fs/file-name f)]
    (and (.endsWith name ".xml")
         (not (.startsWith name "."))
         (not (some #{".git"} (->> f fs/absolutize fs/components (map str)))))))

(defn file->id
  [f]
  (str (fs/relativize *dir* f)))

(defn id->file
  [id]
  (fs/file *dir* id))

(defn file->desc
  [f]
  {:file f
   :id   (file->id f)})

(defn article-descs
  ([]
   (article-descs *dir*))
  ([dir]
   (->> (file-seq (fs/file dir))
        (filter article-file?)
        (map #(file->desc %)))))

(def changes
  (a/chan 16 (map #(into [] (map id->file) %))))

(def changes-mult
  (a/mult changes))

(def lock
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
  (sh/with-sh-dir *dir*
    (let [result   (apply sh (concat ["git"] (map str args)))
          success? (zero? (:exit result))
          ctx      {::dir    *dir*
                    ::args   args
                    ::result result}]
      (tel/with-ctx+ {::git ctx}
        (tel/event! ::sh (if success? :debug :error))
        (when-not success? (throw (ex-info (str args) ctx)))
        result))))

(defmethod ig/init-key ::repository
  [_ _]
  (let [ctx {::dir *dir* ::origin *origin* ::branch *branch*}]
    (with-git
      (tel/with-ctx+ ctx
        (tel/event! ::init :info)
        (when-not (fs/directory? *dir* ".git")
          (if *origin*
            (git! "clone" "--quiet" *origin* ".")
            (do (fs/create-dirs *dir*) (git! "init" "--quiet"))))
        (let [head (->> (git! "symbolic-ref" "--short" "-q" "HEAD") :out str/trim)]
          (when-not (= *branch* head)
            (if *origin*
              (git! "checkout" "--track" (str "origin/" *branch*))
              (git! "checkout" "-b" *branch*))))
        ctx))))

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
  (when *origin*
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

(defn changed-ids
  []
  (with-open [_ (metrics/timed! status-timer)]
    (->> (git! "status" "-s" "--porcelain") :out str/split-lines
         (into [] (comp (map not-empty) (remove nil?) (mapcat status->paths))))))

(defn dirty?
  []
  (seq (changed-ids)))

(defn assert-clean
  []
  (when (dirty?) (throw (IllegalStateException. "Git dir is dirty."))))

(def commit-timer
  (metrics/timer "git.commit"))

(defn commit!
  []
  (when-let [ids (with-git
                   (let [ids (changed-ids)]
                     (when (seq ids)
                       (with-open [_ (metrics/timed! commit-timer)]
                         (git! "commit" "-a" "-m" "zdl-lex-server"))
                       ids)))]
    (a/>!! changes ids)
    (push!)))

(defn sync!
  []
  (fetch!)
  (commit!))

(defn get-article-file
  [id]
  (let [f (fs/file *dir* id)]
    (when (fs/regular-file? f) f)))

(defn write-article-file
  [id write-fn]
  (let [f (fs/file *dir* id)]
    (with-git
      (let [exists? (fs/regular-file? f)]
        (when-not exists? (-> f fs/parent fs/create-dirs))
        (with-open [output (io/output-stream f)] (write-fn output))
        (when-not exists? (add! id))))
    (a/>!! changes (list (file->id f)))
    f))

(defn head-rev
  []
  (->> (git! "rev-parse" "HEAD") :out str/trim))

(defn diff-changed-ids
  [prev-head]
  (->> (git! "diff" "--numstat" (str prev-head ".." "HEAD"))
       :out (str/split-lines)
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
        (a/>!! changes (diff-changed-ids prev-head))))
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
        (a/>!! changes (diff-changed-ids prev-head))
        (push!)
        (resp/response {:ff ref})
        (catch Throwable t
          (tel/error! {:error t :level :warn})
          (git! "rebase" "--abort")
          (-> {:ff ref} (resp/response) (resp/status 400)))))))
