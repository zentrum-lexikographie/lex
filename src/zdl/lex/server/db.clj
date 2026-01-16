(ns zdl.lex.server.db
  (:require
   [pg.core :as pg]
   [pg.migration.core :as pmig]
   [zdl.lex.env :as env]
   [pg.honey :as pgh]))

(def ^:dynamic db
  nil)

(defn close!
  []
  (when db (pg/close db) (alter-var-root #'db (constantly nil))))

(defn open!
  []
  (close!)
  (pmig/migrate-all env/db)
  (alter-var-root #'db (constantly (pg/pool env/db))))

(defn q
  ([sql]
   (q env/db sql))
  ([c sql]
  (pgh/query c sql {:honey {:inline true}})))

