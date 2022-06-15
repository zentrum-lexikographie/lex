(ns zdl.lex.server.git
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [metrics.timers :as timers]
   [slingshot.slingshot :refer [throw+ try+]]
   [zdl.lex.article :as article]
   [zdl.lex.article.validate :as article.validate]
   [zdl.lex.fs :as fs]
   [zdl.lex.git :as git]
   [zdl.lex.server.solr.client :as solr.client]
   [zdl.lex.server.solr.fields :as solr.fields]
   [integrant.core :as ig])
  (:import
   (java.io File)
   (java.util Date)
   (java.util.concurrent TimeUnit)
   (java.util.concurrent.locks ReentrantLock)))

(defn article?
  [^File f]
  (let [name (.getName f)
        path (.getAbsolutePath f)]
    (and
     (.endsWith name ".xml")
     (not (.startsWith name "."))
     (not (#{"__contents__.xml" "indexedvalues.xml"} name))
     (not (.contains path ".git")))))

(defn file->desc
  [dir f]
  {:id   (str (fs/relativize dir f))
   :file (fs/file f)})

(defn files-xf
  [dir]
  (comp
   (map fs/file)
   (filter article?)
   (map (partial file->desc dir))))

(defn removal-xf
  [dir]
  (comp
   (files-xf dir)
   (remove (comp fs/file? :file))
   (map :id)))

(defn remove!
  [dir vs]
  (solr.client/remove! (sequence (removal-xf dir) vs)))

(defn desc->article
  [{:keys [file] :as desc}]
  (try
    (let [xml     (article/read-xml file)
          article (article/extract-article xml)
          errors  (article.validate/check-for-errors xml file)]
      (merge desc article errors))
    (catch Throwable t
      (log/warnf t "Error parsing %s" file)
      desc)))

(defn articles-xf
  [dir]
  (comp
   (files-xf dir)
   (filter (comp fs/file? :file))
   (map desc->article)))

(defn index-xf
  [dir]
  (comp
   (articles-xf dir)
   (map solr.fields/article->doc)))

(defn update!
  [dir vs]
  (solr.client/add! (sequence (index-xf dir) vs)))

(defn refresh!
  [dir]
  (let [threshold (Date.)]
    (update! dir (file-seq (io/file dir)))
    (solr.client/purge! "article" threshold)))

(def lock
  (ReentrantLock.))

(def default-timeout
  30000)

(defn lock!
  ([]
   (lock! default-timeout))
  ([timeout]
   (.tryLock lock timeout TimeUnit/MILLISECONDS)))

(defn unlock!
  []
  (.unlock lock))

(defn with-lock
  ([f]
   (with-lock default-timeout f))
  ([timeout f]
   (when-not (lock! timeout)
     (throw+ {:type ::lock-timeout ::timeout timeout ::f f}))
   (try (f) (finally (unlock!)))))

(def gc-timer
  (timers/timer ["git" "local" "gc-timer"]))

(defn gc!
  [dir]
  (->>
   (git/sh! dir "gc" "--aggressive")
   (timers/time! gc-timer)))

(def fetch-timer
  (timers/timer ["git" "remote" "fetch-timer"]))

(defn fetch!
  [{:keys [origin dir]}]
  (when origin
    (->>
     (git/sh! dir "fetch" "--quiet" "origin" "--tags")
     (timers/time! fetch-timer))))

(def push-timer
  (timers/timer ["git" "remote" "push-timer"]))

(defn push!
  [{:keys [origin branch dir]}]
  (when origin
    (->>
     (git/sh! dir "push" "--quiet" "origin" branch)
     (timers/time! push-timer))))

(def status-timer
  (timers/timer ["git" "local" "status-timer"]))

(defn- status
  [dir]
  (->>
   (git/status dir)
   (timers/time! status-timer)))

(defn add!
  [dir f]
  (with-lock (fn [] (git/sh! dir "add" (fs/path f)))))

(def commit-timer
  (timers/timer ["git" "local" "commit-timer"]))

(defn changes-xf
  [dir]
  (comp
   (mapcat :paths)
   (map #(fs/file dir %))
   (mapcat #(concat (update! dir %) (remove! dir %)))))

(defn publish-changes!
  [dir changes]
  (into [] (changes-xf dir) changes))

(defn commit!
  [{:keys [dir] :as repo}]
  (with-lock
    (fn []
      (when-let [changes (not-empty (status dir))]
        (->>
         (git/sh! dir "commit" "-a" "-m" "zdl-lex-server")
         (timers/time! commit-timer))
        (publish-changes! dir changes)
        (push! repo)))))

(def diff-xf
  (comp
   (map not-empty) (remove nil?)
   (map #(str/split % #"\t"))
   (map #(nth % 2))))

(defn fast-forward!
  [[{:keys [dir] :as repo}] ref]
  (fetch! repo)
  (with-lock
    (fn []
      (let [head (git/head-rev dir)]
        (git/assert-clean dir)
        (git/sh! dir "merge" "--ff-only" "-q" ref)
        (let [diff (git/sh! dir "diff" "--numstat" (str head ".." "HEAD"))
              diff-out (str/split-lines (get diff :out))
              changes (sequence diff-xf diff-out)]
          (publish-changes! dir changes)))))
  (push! repo))

(defn handle-fast-forward
  [repo req]
  (let [ref (get-in req [:parameters :path :ref])]
    (try
      {:status 200
       :body   (fast-forward! repo ref)}
      (catch Throwable t
        (log/warn t)
        {:status 400
         :body   ref}))))

(defn get-file
  [dir path]
  (when-let [f (fs/file dir path)] f))

(defn edit!
  ([dir path editor]
   (edit! dir default-timeout path editor))
  ([dir lock-timeout path editor]
   (with-lock
     lock-timeout
     (fn []
       (let [f       (fs/file dir path)
             exists? (fs/file? f)]
         (when-not exists? (.. f (getParentFile) (mkdirs)))
         (try+
          (editor f)
          (when-not exists? (add! dir f))
          (update! dir [f])
          (catch [:type ::unmodified] _))
         f)))))

(defmethod ig/init-key ::repo
  [_ {:keys [branch origin dir] :as config}]
  (with-lock
    (fn []
      (let [f    (fs/file dir)
            path (fs/path dir)]
        (log/info {:git {:repo path :branch branch :origin origin}})
        (when-not (fs/directory? f ".git")
          (if origin
            (do
              (log/info {:git {:clone origin}})
              (git/sh! dir "clone" "--quiet" origin path))
            (do
              (log/info {:git {:init path}})
              (fs/ensure-dirs f)
              (git/sh! dir "init" "--quiet" path))))
        (when-not (= branch (git/head-ref dir))
          (if origin
            (git/sh! dir "checkout" "--track" (str "origin/" branch))
            (git/sh! dir "checkout" "-b" branch)))
        (assoc config :dir dir :path path)))))

(comment
  (let [dir         (io/file "data" "git")
        articles-xf (articles-xf dir)
        removal-xf  (removal-xf dir)]
    (count (sequence articles-xf (file-seq dir)))
    (into [] (comp articles-xf (drop 1000) (take 10)) (file-seq dir))
    (into [] (comp removal-xf (take 10)) (list (io/file dir "Duden/test.xml")))))
