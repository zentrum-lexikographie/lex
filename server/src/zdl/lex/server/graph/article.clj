(ns zdl.lex.server.graph.article
  (:require [datalevin.core :as d]
            [zdl.lex.article :as article]
            [zdl.lex.server.graph.db :as graph-db]))

(defn article->entity
  [i {:keys [pos provenance source] :as article}]
  (cond->
   {:db/id       (- (inc i))
    :zdl/id      (article :id)
    :zdl/anchors (article :ref-ids)
    :zdl/type    (article :type)
    :zdl/status  (article :status)
    :zdl/form    (article :forms)}
    pos        (assoc :zdl/pos (first pos))
    source     (assoc :zdl/source source)
    provenance (assoc :zdl/provenance provenance)))

(comment
  (time
   (doall
    (map
     (partial d/transact! graph-db/conn)
     (partition-all
      10000
      (map-indexed
       article->entity
       (take
        100
        (article/articles "../../zdl-wb")))))))

  (take 10 (article/articles "../../zdl-wb"))
  (time
   (take
    10
    (d/q
     '[:find [(pull ?a [*]) ...]
       :where
       [?a :zdl/type "Minimalartikel"]
       [?a :zdl/status "Red-f"]]
     (d/db graph-db/conn)))))
