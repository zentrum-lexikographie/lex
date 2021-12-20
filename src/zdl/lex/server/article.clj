(ns zdl.lex.server.article
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [zdl.lex.article :as article]
            [zdl.lex.data :as data]
            [zdl.lex.cron :as cron]
            [zdl.lex.server.solr.client :as solr.client]
            [zdl.lex.server.solr.fields :as solr.fields])
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
    (solr.client/add! (map solr.fields/article->doc articles))))

(defn remove!
  [article-files]
  (let [article-files (map describe-article-file article-files)
        article-ids   (vec (map :id article-files))]
    (log/debugf "Removing %d article(s)" (count article-ids))
    (solr.client/remove! article-ids)))

(defn refresh!
  []
  (let [article-files (article/files dir)
        threshold     (Date.)]
    (log/debugf "Refreshing %d article(s)" (count article-files))
    (doseq [article-batch (partition-all 10000 article-files)]
      (update! article-batch))
    (log/debugf "Purging article(s) before %s" threshold)
    (solr.client/purge! "article" threshold)))

(defstate scheduled-refresh
  :start (cron/schedule "0 0 1 * * ?" "Refresh all articles" refresh!)
  :stop (a/close! scheduled-refresh))
