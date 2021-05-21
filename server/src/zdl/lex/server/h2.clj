(ns zdl.lex.server.h2
  (:require [zdl.lex.data :as data]
            [zdl.lex.fs :as fs])
  (:import java.net.URI
           org.h2.jdbcx.JdbcConnectionPool))

(defn open!
  [id]
  (let [jdbc-uri (str (URI. "jdbc:h2" (fs/path (data/file id)) nil))]
    {:datasource (JdbcConnectionPool/create jdbc-uri "sa" "")}))

(defn close!
  [{:keys [datasource]}]
  (.dispose ^JdbcConnectionPool datasource))

(defn delete!
  [id]
  (doseq [suffix [".mv.db" ".trace.db"]]
    (fs/delete! (data/file (str id suffix)) true)))
