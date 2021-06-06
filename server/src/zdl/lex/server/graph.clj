(ns zdl.lex.server.graph
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [clojure.core.memoize :as memo]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [hugsql.core :refer [def-db-fns]]
            [manifold.deferred :as d]
            [mount.core :as mount :refer [defstate]]
            [zdl.lex.server.h2 :as h2]))

(def-db-fns "zdl/lex/server/graph/schema.sql")

(defstate db
  :start (let [db (h2/open! "graph")]
           (jdbc/with-db-connection [c db]
             (create-zdl-lex-article-table c)
             (create-zdl-lex-article-last-modified-index c)
             (create-zdl-lex-article-link-table c)
             (create-lex-info-form-table c)
             (create-lex-info-form-query-index c)
             (create-mantis-issue-table c)
             (create-mantis-issue-form-index c)
             db))
  :stop (h2/close! db))

(defn transact!
  [f & {:as opts}]
  (->
   (d/future (jdbc/with-db-transaction [c db opts] (f c)) true)
   (d/catch (fn [e]
              (log/warn e "Error while transacting to graph")
              true))))

(def ->kebab-case-keyword*
  (memo/fifo csk/->kebab-case-keyword {} :fifo/threshold 512))

(defn transform-keys*
  [m]
  (cske/transform-keys ->kebab-case-keyword* m))

(defn sql-result
  [v]
  (cond
    (map? v)  (transform-keys* v)
    (coll? v) (map transform-keys* v)
    :else     v))
