(ns zdl.lex.server.graph.article
  (:require [datalevin.core :as dl]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.graph.db :as graph-db]
            [clojure.tools.logging :as log]
            [zdl.lex.server.git :as git])
  (:import java.util.Date))

(defn article->entity
  [i {:keys [anchors links pos provenance source] :as article}]
  (cond->
   {:db/id           (- (inc i))
    :lexinfo/form    (article :forms)
    :zdl-lex/id      (article :id)
    :zdl-lex/type    (article :type)
    :zdl-lex/status  (article :status)}
    (seq anchors) (assoc :zdl-lex/anchors anchors)
    (seq links)   (assoc :zdl-lex/links (distinct (map :anchor links)))
    pos           (assoc :zdl-lex/pos pos)
    provenance    (assoc :zdl-lex/provenance provenance)
    source        (assoc :zdl-lex/source source)))

(defn assoc-last-modified
  [last-modified entity]
  (assoc entity :zdl-lex/last-modified last-modified))

(defn transact!
  [tx]
  (log/infof "Transacting %d ops to graph" (count tx))
  (dl/transact! graph-db/conn tx))

(defn batch-transact!
  [xform coll]
  (->
   (d/future
     (dorun
      (sequence (comp xform (partition-all 1000) (map transact!)) coll))
     true)
   (d/catch
       (fn [e]
         (log/warn e "Error while transacting articles to graph")
         true))))

(defn add-to-graph
  ([articles]
   (add-to-graph (Date.) articles))
  ([last-modified articles]
   (batch-transact!
    (comp
     (map-indexed article->entity)
     (map (partial assoc-last-modified last-modified)))
    articles)))

(defn remove-from-graph
  [articles]
  (batch-transact!
   (map (fn [{:keys [id]}] [:db.fn/retractEntity [:zdl-lex/id id]]))
   articles))

(defn remove-from-graph-before
  [dt]
  (batch-transact!
   (comp
    (take-while #(< (compare (:v %) dt) 0))
    (map (fn [{:keys [e]}] [:db.fn/retractEntity e])))
   (dl/datoms (dl/db graph-db/conn) :ave :zdl-lex/last-modified)))

(comment
  (remove-from-graph-before #inst "2021-05-20T19:07:29.320-00:00"))

(defn rebuild-graph
  [articles]
  (let [now (Date.)]
    (d/chain
     (add-to-graph now articles)
     (fn [_] (remove-from-graph-before now)))))

(comment
  @(rebuild-graph
    (mapcat zdl.lex.article/extract-articles
            (zdl.lex.article/article-files git/dir))))

(defstate article-events->graph
  :start (let [updates  (bus/subscribe article/events :updated)
               removals (bus/subscribe article/events :removed)
               refreshs (bus/subscribe article/events :refresh)]
           (s/consume-async add-to-graph updates)
           (s/consume-async remove-from-graph removals)
           (s/consume-async #(rebuild-graph (%)) refreshs)
           [updates removals refreshs])
  :stop (doseq [s article-events->graph]
          (s/close! s)))

(defn sample-entities
  [_]
  {:status 200
   :body   (take 10
                 (dl/q
                  '[:find [(pull ?a [*]) ...]
                    :where
                    [?a :zdl-lex/status "Red-f"]]
                  (dl/db graph-db/conn)))})
