(ns zdl.lex.server.graph.db
  (:require [datalevin.core :as dl]
            [mount.core :as mount :refer [defstate]]
            [zdl.lex.data :as data]
            [zdl.lex.fs :as fs]))

(def dir
  (data/dir "graph"))

(def schema
  {:zdl-lex/id            {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one
                           :db/unique      :db.unique/identity}
   :zdl-lex/anchors       {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/many}
   :zdl-lex/last-modified {:db/valueType   :db.type/instant
                           :db/cardinality :db.cardinality/one}
   :zdl-lex/links         {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/many}
   :zdl-lex/pos           {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one}
   :zdl-lex/provenance    {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one}
   :zdl-lex/source        {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one}
   :zdl-lex/status        {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one}
   :zdl-lex/type          {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/one}
   :lexinfo/form          {:db/valueType   :db.type/string
                           :db/cardinality :db.cardinality/many}})

(defstate conn
  :start (dl/get-conn (str dir) schema)
  :stop (dl/close conn))

(defn delete!
  []
  (fs/delete! dir))

(comment
  (mount/start #'conn)
  (mount/stop)
  (delete!))
