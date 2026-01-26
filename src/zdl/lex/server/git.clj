(ns zdl.lex.server.git
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh :refer [sh]]
   [clojure.string :as str]
   [integrant.core :as ig]
   [ring.util.response :as resp]
   [taoensso.telemere :as tm]
   [zdl.lex.article :as article]
   [zdl.lex.article.qa :as article.qa]
   [zdl.lex.lucene :as lucene]
   [zdl.lex.metrics :as metrics]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.lock :as lock :refer [with-lock]])
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
  [dir f]
  (str (fs/relativize dir f)))

(defn id->file
  [dir id]
  (fs/file dir id))

(defn file->desc
  [dir file]
  {:file file
   :id   (file->id dir file)})

(defn article-descs
  [dir]
  (->> (file-seq (fs/file dir))
       (filter article-file?)
       (map #(file->desc dir %))))

(defn sync-index!
  [{:keys [dir]}]
  (let [threshold (System/currentTimeMillis)]
    (index/upsert-articles! (article-descs dir))
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
  [{:keys [dir]} & args]
  (sh/with-sh-dir dir
    (let [result (apply sh (concat ["git"] (map str args)))]
      (if (= 0 (:exit result))
        (do (tm/event! ::git {:level       :debug
                              :msg         (format "git @ %s : %s" dir args)
                              ::git-dir    dir
                              ::git-args   args
                              ::git-result result})
            result)
        (do (tm/event! ::git {:level       :error
                              :msg         (format "git @ %s : %s" dir args)
                              ::git-dir    dir
                              ::git-args   args
                              ::git-result result})
            (throw (ex-info (str args) result)))))))

(defmethod ig/init-key ::repository
  [_ {:keys [dir origin branch] :as repo}]
  (with-git
    (tm/log! {:id    ::init
              :level :info
              :data  repo})
    (when-not (fs/directory? dir ".git")
      (if origin
        (git! repo "clone" "--quiet" origin ".")
        (do (fs/create-dirs dir) (git! repo "init" "--quiet"))))
    (let [head-ref (->>
                    (git! repo "symbolic-ref" "--short" "-q" "HEAD")
                    :out str/trim)]
      (when-not (= branch head-ref)
        (if origin
          (git! repo "checkout" "--track" (str "origin/" branch))
          (git! repo "checkout" "-b" branch))))
    repo))

(def gc-timer
  (metrics/timer "git.gc"))

(defn gc!
  [repo]
  (with-open [_ (metrics/timed! gc-timer)]
    (git! repo "gc" "--aggressive")))

(def fetch-timer
  (metrics/timer "git.fetch"))

(defn fetch!
  [{:keys [origin] :as repo}]
  (when origin
    (with-open [_ (metrics/timed! fetch-timer)]
      (git! repo "fetch" "--quiet" "origin" "--tags"))))

(def push-timer
  (metrics/timer "git.push"))

(defn push!
  [{:keys [origin branch] :as repo}]
  (when origin
    (with-open [_ (metrics/timed! push-timer)]
      (git! repo "push" "--quiet" "origin" branch))))

(defn add!
  [repo f]
  (with-git (git! repo "add" (fs/path f))))

(defn status->paths
  [status-line]
  (->> (str/split (subs status-line 3) #"->")
       (map #(str/replace % #"\"" ""))
       (map not-empty) (remove nil?)))

(def status-timer
  (metrics/timer "git.status"))

(defn changed-ids
  [repo]
  (with-open [_ (metrics/timed! status-timer)]
    (->> (git! repo "status" "-s" "--porcelain") :out str/split-lines
         (into [] (comp (map not-empty) (remove nil?) (mapcat status->paths))))))

(defn dirty?
  [repo]
  (seq (changed-ids repo)))

(defn assert-clean
  [repo]
  (when (dirty? repo) (throw (IllegalStateException. "Git dir is dirty."))))

(defn ->index!
  [{:keys [dir]} ids]
  (let [files    (map #(id->file dir %) ids)
        existing (filter fs/regular-file? files)
        removed  (remove fs/regular-file? files)]
    (index/upsert-articles! (map #(file->desc dir %) existing))
    (index/remove! (map #(file->id dir %) removed))))

(def commit-timer
  (metrics/timer "git.commit"))

(defn commit!
  [repo]
  (when-let [ids (with-git
                   (let [ids (changed-ids repo)]
                     (when (seq ids)
                       (with-open [_ (metrics/timed! commit-timer)]
                         (git! repo "commit" "-a" "-m" "zdl-lex-server"))
                       ids)))]
    (->index! repo ids)
    (push! repo)))

(defn sync!
  [repo]
  (fetch! repo)
  (commit! repo))

(defn get-article-file
  [{:keys [dir]} id]
  (let [f (fs/file dir id)]
    (when (fs/regular-file? f) f)))

(defn write-article-file
  [{:keys [dir] :as repo} id write-fn]
  (let [f (fs/file dir id)]
    (with-git
      (let [exists? (fs/regular-file? f)]
        (when-not exists? (-> f fs/parent fs/create-dirs))
        (with-open [output (io/output-stream f)] (write-fn output))
        (when-not exists? (add! repo id))))
    (index/upsert-articles! (list (file->desc dir f)))
    f))

;; Article Editors

(def server-lock-token
  (-> (UUID/randomUUID) str str/lower-case))

(defn qa-article!
  [db repo {:keys [file id]}]
  (try
    (binding [lock/*context* {:owner    "zdl-lex-server"
                              :resource id
                              :token    server-lock-token}]
      (with-lock db
        (fn []
          (when-let [edited (article.qa/edit file)]
            (write-article-file repo id #(article/write-xml edited %))))))
    (catch Throwable t
      ;; Skip locked articles
      (if (lock/locked? t) (tm/error! t) (throw t)))))

(defn qa!
  [db {:keys [dir] :as repo}]
  (run! #(qa-article! db repo %) (article-descs dir)))

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
          num-found (get-in response [:body "response" "numFound"] 1)]
      (cond
        (= 0 num-found) id
        (= 10 n)        (throw (ex-info (str "Maximum number of article id "
                                             "generations exceeded") {}))
        :else           (recur (inc n))))))

(def new-article-collection
  "Neuartikel/Neuartikel-007")

(defn handle-create
  [repo {{:keys [user]} :identity {{:keys [form pos]} :query} :parameters}]
  (let [xml-id   (generate-id)
        filename (article/form->filename form)
        resource (str new-article-collection "/" filename "-" xml-id ".xml")
        xml      (article/new-article-xml xml-id form pos user)]
    (-> (write-article-file repo resource #(spit % xml :encoding "UTF-8"))
        (resp/response)
        (resp/header "X-Lex-Id" resource))))

(defn handle-read
  [repo {{{:keys [resource]} :path} :parameters}]
  (if-let [f (get-article-file repo resource)]
    (resp/response f)
    (resp/not-found resource)))

(defn handle-write
  [db repo {:keys [body] {{:keys [resource]} :path} :parameters}]
  (try
    (with-lock db
      (fn []
        (if-not (get-article-file repo resource)
          (resp/not-found resource)
          (-> (write-article-file repo resource #(io/copy body %)) (resp/response)))))
    (catch Throwable t
      (if (lock/locked? t)
        (-> t ex-data :lock (resp/response) (resp/status 423))
        (throw t)))))

(defn head-rev
  [repo]
  (->> (git! repo "rev-parse" "HEAD") :out str/trim))

(defn diff-changed-ids
  [repo prev-head]
  (->> (git! repo "diff" "--numstat" (str prev-head ".." "HEAD"))
       :out (str/split-lines)
       (into [] (comp (map not-empty) (remove nil?)
                      (map #(str/split % #"\t"))
                      (map #(nth % 2))))))

(defn handle-fast-forward
  [repo {{{:keys [ref]} :path} :parameters}]
  (try
    (fetch! repo)
    (with-git
      (let [prev-head (head-rev repo)]
        (assert-clean repo)
        (git! repo "merge" "--ff-only" "-q" ref)
        (->index! repo (diff-changed-ids repo prev-head))))
    (push! repo)
    (resp/response {:ff ref})
    (catch Throwable t
      (tm/error! {:error t :level :warn})
      (-> ref (resp/response) (resp/status 400)))))

(defn handle-rebase
  [repo {{{:keys [ref]} :path} :parameters}]
  (with-git
    (sync! repo)
    (let [prev-head (head-rev repo)]
      (try
        (git! repo "rebase" ref)
        (->index! repo (diff-changed-ids repo prev-head))
        (push! repo)
        (resp/response {:ff ref})
        (catch Throwable t
          (tm/error! {:error t :level :warn})
          (git! repo "rebase" "--abort")
          (-> {:ff ref} (resp/response) (resp/status 400)))))))

(defn handlers
  [db repo]
  [""
   ["/"
    {:put {:handler    (partial handle-create repo)
           :parameters {:query [:map
                                [:form :string]
                                [:pos :string]]}}}]
   ["/*resource"
    {:get  {:handler    (partial handle-read repo)
            :parameters {:path [:map [:resource :string]]}}
     :post {:handler    (partial handle-write db repo)
            :parameters {:path  [:map [:resource :string]]
                         :query [:map [:token :string]]}}}]])
