(ns zdl-lex-server.store
  (:require [mount.core :refer [defstate]]
            [me.raynes.fs :as fs]
            [hawk.core :as hawk]
            [taoensso.timbre :as timbre]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store]
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

(def article-files (xml-files articles-dir))

(def sample-article (partial rand-nth article-files))

(defstate git-clone
  :start (when-not (fs/directory? store/git-dir)
           (sh/sh "git" "clone"
                  (config :git-repo)
                  (.getAbsolutePath store/git-dir))))

(defstate watcher
  :start (hawk/watch! (config :watcher-opts)
                      [{:paths [articles-dir]
                        :filter #(xml-file? (:file %2))
                        :handler (fn [ctx e] (timbre/debug e) ctx)}])
  :stop (hawk/stop! watcher))
