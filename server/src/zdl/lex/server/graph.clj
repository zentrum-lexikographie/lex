(ns zdl.lex.server.graph
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hugsql.core :refer [def-db-fns]]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [mount.core :as mount :refer [defstate]]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.auth :as auth]
            [zdl.lex.server.h2 :as h2]
            [zdl.lex.article.fs :as afs]
            [zdl.lex.server.git :as git])
  (:import java.util.Date))

(def-db-fns "zdl/lex/server/graph.sql")

(defstate db
  :start (let [db (h2/open! "graph")]
           (jdbc/with-db-connection [c db]
             (create-zdl-lex-article-table c)
             (create-zdl-lex-article-last-modified-index c)
             (create-zdl-lex-article-link-table c)
             (create-lex-info-form-table c)
             (create-lex-info-form-query-index c)
             db))
  :stop (h2/close! db))

(defn delete-article
  [c id]
  (delete-lex-info-forms c {:id id :type "zdl-lex"})
  (delete-zdl-lex-article-links c {:id id})
  (delete-zdl-lex-article c {:id id}))

(defn article->db
  [c last-modified {:keys [id] :as article}]
  (delete-article c id)
  (insert-zdl-lex-article c {:id id
                             :last-modified last-modified
                             :status (article :status)
                             :type (article :type)
                             :pos (article :pos)
                             :provenance (article :provenance)
                             :source (article :source)})
  (doseq [anchor (distinct (article :anchors))]
    (insert-zdl-lex-article-link c {:id id :anchor anchor :incoming true}))
  (doseq [anchor (distinct (map :anchor (article :links)))]
    (insert-zdl-lex-article-link c {:id id :anchor anchor :incoming false}))
  (doseq [form (distinct (article :forms))]
    (insert-lex-info-form c {:form form :id id :type "zdl-lex"})))

(defn transact!
  [f]
  (->
   (d/future (jdbc/with-db-transaction [c db] (f c)) true)
   (d/catch (fn [e]
              (log/warn e "Error while transacting articles to graph")
              true))))

(defn add-to-graph
  [articles]
  (transact!
   (fn [c]
     (let [now (Date.)]
       (log/debugf "Adding %d articles to graph" (count articles))
       (doseq [article articles]
         (article->db c now article))))))

(defn remove-from-graph
  [articles]
  (transact!
   (fn [c]
     (log/infof "Removing %d article(s)" (count articles))
     (doseq [{:keys [id]} articles]
       (delete-article c id)))))

(defn remove-from-graph-before
  [threshold]
  (transact!
   (fn [c]
     (log/infof "Purging articles before %s" threshold)
     (doseq [{:keys [id]} (select-outdated-zdl-lex-articles
                           c {:threshold threshold})]
       (delete-article c id)))))

(defstate article-events->graph
  :start (let [updates  (s/batch
                         1000 1000 (bus/subscribe article/events :updated))
               removals (s/batch
                         1000 1000 (bus/subscribe article/events :removed))
               purges (bus/subscribe article/events :purge)]
           (s/consume-async add-to-graph updates)
           (s/consume-async remove-from-graph removals)
           (s/consume-async remove-from-graph-before purges)
           [updates removals purges])
  :stop (doseq [s article-events->graph]
          (s/close! s)))

(defn get-sample-links
  [_]
  {:status 200
   :body (jdbc/with-db-transaction [c db {:read-only? true}]
           (vec (select-sample-links c)))})
