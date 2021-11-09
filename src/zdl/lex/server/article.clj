(ns zdl.lex.server.article
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [zdl.lex.article :as article]
            [zdl.lex.data :as data]
            [zdl.lex.server.graph.article :as graph.article]
            [zdl.lex.server.solr.article :as solr.article])
  (:import java.util.Date))

(def dir
  (data/dir "git"))

(defn describe-article-file
  [f]
  (article/describe-article-file dir f))

(defn file->articles
  [article-file]
  (article/extract-articles (describe-article-file article-file)))

(defn update!
  [article-files]
  (let [articles (vec (flatten (pmap file->articles article-files)))]
    (log/debugf "Updating %d article(s)" (count articles))
    (a/<!!
     (a/into
      []
      (a/merge
       [(solr.article/update! articles)
        (a/thread (graph.article/update! articles))])))))

(defn remove!
  [article-files]
  (let [article-files (map describe-article-file article-files)
        article-ids   (vec (map :id article-files))]
    (log/debugf "Removing %d article(s)" (count article-ids))
    (a/<!!
     (a/into
      []
      (a/merge
       [(solr.article/remove! article-ids)
        (a/thread (graph.article/remove! article-ids))])))))

(defn refresh!
  []
  (let [article-files (article/files dir)
        threshold     (Date.)]
    (log/debugf "Refreshing %d article(s)" (count article-files))
    (doseq [article-batch (partition-all 10000 article-files)]
      (update! article-batch))
    (log/debugf "Purging article(s) before %s" threshold)
    (a/<!!
     (a/into
      []
      (a/merge
       [(solr.article/purge! threshold)
        (a/thread (graph.article/purge! threshold))])))))
