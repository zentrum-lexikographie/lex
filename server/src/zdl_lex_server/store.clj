(ns zdl-lex-server.store
  (:require [me.raynes.fs :as fs]
            [environ.core :refer [env]]))

(def data-dir (-> (env :zdl-lex-data-dir "../data")
                  fs/file fs/absolute fs/normalized))

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

(comment
  (take 10 (article-files)))
