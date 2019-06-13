(ns zdl-lex-server.store
  (:require [mount.core :refer [defstate]]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.env :refer [config]]
            [clojure.java.shell :as sh]))

(def data-dir (-> config :data-dir fs/file fs/absolute fs/normalized))

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

(defn sample-article [] (rand-nth (article-files)))

(def mantis-dump (fs/file data-dir "mantis.edn"))
