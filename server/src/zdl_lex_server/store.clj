(ns zdl-lex-server.store
  (:require [mount.core :refer [defstate]]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.env :refer [config]]
            [clojure.java.shell :as sh]))

(def data-dir (-> config :data-dir fs/file fs/absolute))

(def git-dir (fs/file data-dir "git"))
(def articles-dir (fs/file git-dir "articles"))

(defn relative-article-path [article-file]
  (str (.. articles-dir (toPath) (relativize (.toPath article-file)))))

(defn xml-file? [f]
  (let [name (.getName f)
        path (.getAbsolutePath f)]
    (and 
     (.endsWith name ".xml")
     (not (.startsWith name "."))
     (not (#{"__contents__.xml" "indexedvalues.xml"} name))
     (not (.contains path ".git")))))

(defn xml-files [dir] (filter xml-file? (file-seq (fs/file dir))))

(def article-files (partial xml-files articles-dir))

(defn sample-article [] (rand-nth (article-files)))

(defstate git-clone
  :start (do
           (when-not (fs/directory? git-dir)
             (sh/sh "git" "clone" (config :git-repo) (.getAbsolutePath git-dir)))
           (when-not (fs/directory? articles-dir)
             (fs/mkdirs articles-dir))
           git-dir))
