(ns zdl-lex-server.store
  (:require [me.raynes.fs :as fs]))

(def base-dir (fs/parent fs/*cwd*))
(def data-dir (fs/file base-dir "data"))

(def exist-export-file (fs/file data-dir "exist-db-export.zip"))
(def exist-dir (fs/file data-dir "exist-db"))

(def git-repo-dir (fs/file data-dir "repo.git"))
(def git-checkout-dir (fs/file data-dir "git"))

(def articles-dir (fs/file git-checkout-dir "articles"))

(defn relative-article-path [article-file]
  (str (.. articles-dir (toPath) (relativize (.toPath article-file)))))

(defn xml-files [dir]
  (->> (file-seq dir)
       (map #(.getAbsolutePath %))
       (filter #(.endsWith % ".xml"))
       (remove #(.endsWith % "__contents__.xml"))
       (remove #(.endsWith % "indexedvalues.xml"))
       (remove #(.contains % ".git"))
       (map fs/file)))
