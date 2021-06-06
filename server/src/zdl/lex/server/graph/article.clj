(ns zdl.lex.server.graph.article
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hugsql.core :refer [def-db-fns]]
            [manifold.bus :as bus]
            [manifold.stream :as s]
            [mount.core :as mount :refer [defstate]]
            [ubergraph.core :as uber]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.graph :as graph]
            [zdl.lex.server.graph.mantis :as mantis])
  (:import java.util.Date))

(def-db-fns "zdl/lex/server/graph/article.sql")

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

(defn add-to-graph
  [articles]
  (graph/transact!
   (fn [c]
     (let [now (Date.)]
       (log/debugf "Adding %d articles to graph" (count articles))
       (doseq [article articles]
         (article->db c now article))))))

(defn remove-from-graph
  [articles]
  (graph/transact!
   (fn [c]
     (log/infof "Removing %d article(s) from graph" (count articles))
     (doseq [{:keys [id]} articles]
       (delete-article c id)))))

(defn remove-from-graph-before
  [threshold]
  (graph/transact!
   (fn [c]
     (log/infof "Purging articles before %s" threshold)
     (doseq [{:keys [id]} (select-outdated-zdl-lex-articles
                           c {:threshold threshold})]
       (delete-article c id)))))

(defstate article-events->graph
  :start (let [updates  (bus/subscribe article/events :updated)
               removals (bus/subscribe article/events :removed)
               purges   (bus/subscribe article/events :purge)
               refreshs (s/batch 1000 10000
                                 (bus/subscribe article/events :refreshed))]
           (s/consume-async add-to-graph updates)
           (s/consume-async add-to-graph refreshs)
           (s/consume-async remove-from-graph removals)
           (s/consume-async remove-from-graph-before purges)
           [updates removals purges])
  :stop (doseq [s article-events->graph]
          (s/close! s)))

(defn anchor->form
  [anchor]
  (let [sep-idx (str/last-index-of anchor "#")]
    (if sep-idx (subs anchor 0 sep-idx) anchor)))

(defn anchor->id
  ([{:keys [anchor incoming]}]
   (anchor->id anchor incoming))
  ([anchor incoming]
   (let [sep-idx (str/last-index-of anchor "#")]
     (str (if sep-idx anchor (str anchor "#1"))
          (if incoming "<" ">")))))

(defn get-article-graph
  [{{{id :resource} :path} :parameters}]
  (jdbc/with-db-transaction [c graph/db {:read-only? true}]
    (if-let [article (graph/sql-result (select-zdl-lex-article-by-id c {:id id}))]
      {:status 200
       :body
       (let [anchors (graph/sql-result (select-zdl-lex-article-links-by-id c {:id id}))
             anchors (map #(assoc % :id (anchor->id %)) anchors)
             anchors (map #(assoc % :form (-> % :anchor anchor->form)) anchors)
             forms   (distinct (map :form anchors))
             links   (graph/sql-result (select-zdl-lex-article-links c {:id id}))
             issues  (mantis/find-issues-by-forms forms)]
         {:nodes
          (concat
           (list
            [id (assoc article :type :zdl-lex-article)])
           (for [anchor anchors]
             [(:id anchor) (assoc (dissoc anchor :id :form) :type :anchor)])
           (for [form forms]
             [form {:type :form :form form}])
           (for [link links]
             [(:id link) (dissoc link :this-anchor :this-incoming)])
           (for [issue issues]
             [(:url issue) (assoc issue :type :issue)]))
          :edges
          (concat
           (for [anchor anchors]
             [id (:id anchor)])
           (for [anchor anchors]
             [(:id anchor) (:form anchor)])
           (for [issue issues]
             [(:form issue) (:url issue)])
           (for [{:keys [this-anchor this-incoming id]} links]
             [(anchor->id this-anchor this-incoming) id]))})}
      {:status 404
       :body id})))

(comment
  (let [graph (get-article-graph {:parameters
                                  {:path
                                   {:resource "WDG/ro/roh-E_r_5749.xml"}}})
        graph (:body graph)]
    (uber/viz-graph (apply uber/multigraph (:edges graph)))))
