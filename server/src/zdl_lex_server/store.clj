(ns zdl-lex-server.store
  (:require [me.raynes.fs :as fs]
            [zdl-lex-common.env :refer [env]]
            [ring.util.request :as htreq]
            [ring.util.http-response :as htstatus]))

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

(comment
  (take 10 (article-files)))

(defn get-article [{{:keys [path]} :path-params}]
  (let [f (id->file path)]
    (if (fs/exists? f)
      (htstatus/ok f)
      (htstatus/not-found path))))

(defn post-article [{{:keys [path]} :path-params :as req}]
  (let [f (id->file path)]
    (if (fs/exists? f)
      (do (spit f (htreq/body-string req) :encoding "UTF-8") (htstatus/ok f))
      (htstatus/not-found path))))

(def ring-handlers
  ["/store/*path" {:get get-article :post post-article}])
