(ns zdl.lex.server.db
  (:require
   [integrant.core :as ig]
   [pg.core :as pg]
   [pg.migration.core :as pmig]
   [pg.honey :as pgh]))

(defmethod ig/init-key ::connection
  [_ db]
  (pmig/migrate-all db)
  (pg/pool db))

(defmethod ig/halt-key! ::connection
  [_ connection]
  (pg/close connection))

(defn q
  [c sql]
  (pgh/query c sql {:honey {:inline true}}))
