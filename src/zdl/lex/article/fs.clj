(ns zdl.lex.article.fs
  (:require [zdl.lex.fs :refer [file]])
  (:import java.io.File))

(defn article-file?
  [^File f]
  (let [name (.getName f)
        path (.getAbsolutePath f)]
    (and
     (.endsWith name ".xml")
     (not (.startsWith name "."))
     (not (#{"__contents__.xml" "indexedvalues.xml"} name))
     (not (.contains path ".git")))))

(defn files
  [dir]
  (->> dir file file-seq (filter article-file?) (map file)))
