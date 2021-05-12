(ns zdl.lex.server.graph.db
  (:require [datalevin.core :as d]
            [mount.core :as mount :refer [defstate]]
            [zdl.lex.data :as data]
            [zdl.lex.fs :as fs]))

(def dir
  (data/dir "graph"))

(def schema
  {:zdl/id         {:db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/unique      :db.unique/identity}
   :zdl/anchors    {:db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/many}
   :zdl/type       {:db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one}
   :zdl/status     {:db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one}
   :zdl/form       {:db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/many}
   :zdl/pos        {:db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one}
   :zdl/provenance {:db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one}})

(defstate conn
  :start (d/get-conn (str dir) schema)
  :stop (d/close conn))

(defn delete!
  []
  (fs/delete! dir))

(comment
  (mount/start #'conn)
  (mount/stop)
  (delete!))
