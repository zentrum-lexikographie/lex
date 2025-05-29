(ns zdl.lex.server.git
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh :refer [sh]]
   [clojure.string :as str]
   [ring.util.response :as resp]
   [zdl.lex.article :as article]
   [zdl.lex.article.qa :as article.qa]
   [zdl.lex.env :as env :refer [git-dir]]
   [zdl.lex.lucene :as lucene]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.lock :as lock :refer [with-lock]]
   [taoensso.telemere :as tm])
  (:import
   (java.util UUID)
   (java.util.concurrent TimeUnit)
   (java.util.concurrent.locks ReentrantLock)))

(defn article-file?
  [f]
  (let [name (fs/file-name f)]
    (and (.endsWith name ".xml")
         (not (.startsWith name "."))
         (not (some #{".git"} (->> f fs/absolutize fs/components (map str)))))))

(defn file->id
  [f]
  (str (fs/relativize git-dir f)))

(defn id->file
  [id]
  (fs/file git-dir id))

(defn file->desc
  [file]
  {:file file
   :id   (file->id file)})

(defn article-descs
  []
  (->> (file-seq (fs/file git-dir))
       (filter article-file?)
       (map file->desc)))

(defn sync-index!
  []
  (let [threshold (System/currentTimeMillis)]
    (index/upsert-articles! (article-descs))
    (index/purge! "article" threshold)))

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
  (sh/with-sh-dir git-dir
    (let [result (apply sh (concat ["git"] (map str args)))]
      (if (= 0 (:exit result))
        (do (tm/event! ::git {:level :debug
                              :msg         (format "git @ %s : %s" git-dir args)
                              ::git-dir    git-dir
                              ::git-args   args
                              ::git-result result})
            result)
        (do (tm/event! ::git {:level       :error
                              :msg         (format "git @ %s : %s" git-dir args)
                              ::git-dir    git-dir
                              ::git-args   args
                              ::git-result result})
            (throw (ex-info (str args) result)))))))

(defn init!
  []
  (with-git
    (let [f    (fs/file git-dir)
          path (fs/path git-dir)]
      (tm/log! :info (format "Init %s#%s -> '%s' "
                            (or env/git-origin "git:local")
                            env/git-branch path))
      (when-not (fs/directory? f ".git")
        (if env/git-origin
          (git! "clone" "--quiet" env/git-origin ".")
          (do (fs/create-dirs f) (git! "init" "--quiet"))))
      (let [head-ref (->>
                      (git! "symbolic-ref" "--short" "-q" "HEAD")
                      :out str/trim)]
        (when-not (= env/git-branch head-ref)
          (if env/git-origin
            (git! "checkout" "--track" (str "origin/" env/git-branch))
            (git! "checkout" "-b" env/git-branch))))
      git-dir)))

(def gc-timer
  (env/timer "git.gc"))

(defn gc!
  []
  (with-open [_ (env/timed! gc-timer)]
    (git! "gc" "--aggressive")))

(def fetch-timer
  (env/timer "git.fetch"))

(defn fetch!
  []
  (when env/git-origin
    (with-open [_ (env/timed! fetch-timer)]
     (git! "fetch" "--quiet" "origin" "--tags"))))

(def push-timer
  (env/timer "git.push"))

(defn push!
  []
  (when env/git-origin
    (with-open [_ (env/timed! push-timer)]
     (git! "push" "--quiet" "origin" env/git-branch))))

(defn add!
  [f]
  (with-git (git! "add" (fs/path f))))

(defn status->paths
  [status-line]
  (->> (str/split (subs status-line 3) #"->")
       (map #(str/replace % #"\"" ""))
       (map not-empty) (remove nil?)))

(def status-timer
  (env/timer "git.status"))

(defn changed-ids
  []
  (with-open [_ (env/timed! status-timer)]
    (->> (git! "status" "-s" "--porcelain") :out str/split-lines
         (into [] (comp (map not-empty) (remove nil?) (mapcat status->paths))))))

(defn dirty?
  []
  (seq (changed-ids)))

(defn assert-clean
  []
  (when (dirty?) (throw (IllegalStateException. "Git dir is dirty."))))

(defn ->index!
  [ids]
  (let [files    (map id->file ids)
        existing (filter fs/regular-file? files)
        removed  (remove fs/regular-file? files)]
    (index/upsert-articles! (map file->desc existing))
    (index/remove! (map file->id removed))))

(def commit-timer
  (env/timer "git.commit"))

(defn commit!
  []
  (when-let [ids (with-git
                   (let [ids (changed-ids)]
                     (when (seq ids)
                       (with-open [_ (env/timed! commit-timer)]
                         (git! "commit" "-a" "-m" "zdl-lex-server"))
                       ids)))]
    (->index! ids)
    (push!)))

(defn sync!
  []
  (fetch!)
  (commit!))

(defn get-article-file
  [id]
  (let [f (fs/file git-dir id)]
    (when (fs/regular-file? f) f)))

(defn write-article-file
  [id write-fn]
  (let [f (fs/file git-dir id)]
    (with-git
      (let [exists? (fs/regular-file? f)]
        (when-not exists? (-> f fs/parent fs/create-dirs))
        (with-open [output (io/output-stream f)] (write-fn output))
        (when-not exists? (add! id))))
    (index/upsert-articles! (list (file->desc f)))
    f))

;; Article Editors

(def server-lock-token
  (-> (UUID/randomUUID) str str/lower-case))

(defn qa-article!
  [{:keys [file id]}]
  (try
    (binding [lock/*context* {:owner    "zdl-lex-server"
                              :resource id
                              :token    server-lock-token}]
      (with-lock
        (when-let [edited (article.qa/edit file)]
          (write-article-file id #(article/write-xml edited %)))))
    (catch Throwable t
      ;; Skip locked articles
      (if (lock/locked? t) (tm/error! t) (throw t)))))

(defn qa!
  []
  (run! qa-article! (article-descs)))

;; Articles

(defn generate-id
  []
  (loop [n 0]
    (let [id        (str "E_" (rand-int 10000000))
          id-query  [:query
                    [:clause
                     [:field [:term "id"]]
                     [:value [:pattern (str "*" id "*")]]]]
          request   {:q (lucene/->str id-query) :rows 0}
          response  (index/query request)
          num-found (get-in response [:body :response :numFound] 1)]
      (cond
        (= 0 num-found) id
        (= 10 n)        (throw (ex-info (str "Maximum number of article id "
                                             "generations exceeded") {}))
        :else           (recur (inc n))))))

(def new-article-collection
  "Neuartikel/Neuartikel-007")

(defn handle-create
  [{{:keys [user]} :identity {{:keys [form pos]} :query} :parameters}]
  (let [xml-id   (generate-id)
        filename (article/form->filename form)
        resource (str new-article-collection "/" filename "-" xml-id ".xml")
        xml      (article/new-article-xml xml-id form pos user)]
    (-> (write-article-file resource #(spit % xml :encoding "UTF-8"))
        (resp/response)
        (resp/header "X-Lex-ID" resource))))

(defn handle-read
  [{{{:keys [resource]} :path} :parameters}]
  (if-let [f (get-article-file resource)]
    (resp/response f)
    (resp/not-found resource)))

(defn handle-write
  [{:keys [body] {{:keys [resource]} :path} :parameters}]
  (try
    (with-lock
      (if-not (get-article-file resource)
        (resp/not-found resource)
        (-> (write-article-file resource #(io/copy body %)) (resp/response))))
    (catch Throwable t
      (if (lock/locked? t)
        (-> t ex-data :lock (resp/response) (resp/status 423))
        (throw t)))))

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
        (->index! (diff-changed-ids prev-head))))
    (push!)
    (resp/response {:ff ref})
    (catch Throwable t
      (tm/error! {:error t :level :warn})
      (-> ref (resp/response) (resp/status 400)))))

(defn handle-rebase
  [{{{:keys [ref]} :path} :parameters}]
  (with-git
    (sync!)
    (let [prev-head (head-rev)]
      (try
        (git! "rebase" ref)
        (->index! (diff-changed-ids prev-head))
        (push!)
        (resp/response {:ff ref})
        (catch Throwable t
          (tm/error! {:error t :level :warn})
          (git! "rebase" "--abort")
          (-> {:ff ref} (resp/response) (resp/status 400)))))))
