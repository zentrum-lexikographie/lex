(ns zdl-lex-server.store
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [tick.alpha.api :as t]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-common.env :refer [env]]
            [ring.util.request :as htreq]
            [ring.util.http-response :as htstatus])
  (:import [java.text Normalizer Normalizer$Form]
           java.util.concurrent.TimeUnit
           java.util.concurrent.locks.ReentrantReadWriteLock))

(def data-dir (env :data-dir))
(def git-dir (fs/file data-dir "git"))
(def articles-dir (fs/file git-dir "articles"))

(defn file->id [article-file]
  (str (.. articles-dir (toPath) (relativize (.toPath article-file)))))

(defn id->file [id]
  (fs/file articles-dir id))

(defn xml-file? [f]
  (let [name (.getName f)
        path (.getAbsolutePath f)]
    (and 
     (.endsWith name ".xml")
     (not (.startsWith name "."))
     (not (#{"__contents__.xml" "indexedvalues.xml"} name))
     (not (.contains path ".git")))))

(defn xml-files [dir] (filter xml-file? (file-seq (fs/file dir))))

(def article-file? (every-pred xml-file? (partial fs/child-of? articles-dir)))

(def article-files (partial xml-files articles-dir))

(defn sample-article []
  (->> (article-files) (drop (rand-int 100000)) (first)))

(def mantis-dump (fs/file data-dir "mantis.edn"))

(defonce lock (ReentrantReadWriteLock.))

(defmacro with-lock
  [lock-method & body]
  `(let [lock# (~lock-method ^ReentrantReadWriteLock lock)]
     (if (.tryLock lock# 30 TimeUnit/SECONDS)
       (try ~@body
            (finally
              (.unlock lock#)))
       (throw (ex-info "Storage lock timeout" {})))))

(defmacro with-read-lock [& body] `(with-lock .readLock ~@body))

(defmacro with-write-lock [& body] `(with-lock .writeLock ~@body))

(comment
  (take 10 (article-files)))

