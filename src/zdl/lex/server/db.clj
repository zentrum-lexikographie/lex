(ns zdl.lex.server.db
  (:require
   [honey.sql :as sql]
   [integrant.core :as ig]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as jdbc.con]
   [next.jdbc.result-set :as jdbc.result-set]
   [ragtime.core]
   [ragtime.next-jdbc]
   [ragtime.repl]
   [taoensso.telemere :as tm])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(defmethod ig/init-key ::connection
  [_ db]
  (ragtime.repl/migrate
   {:datastore  (ragtime.next-jdbc/sql-database db)
    :migrations (ragtime.next-jdbc/load-resources "zdl/lex/server/db")
    :reporter   (fn [_ op id]
                  (tm/event! ::migrate {:level :info :data {:op op :id id}}))})
  (tm/log! {:id ::connect :level :info :data db})
  (jdbc.con/->pool HikariDataSource db))

(defmethod ig/halt-key! ::connection
  [_ connection]
  (.close connection))

(def query-opts
  {:builder-fn jdbc.result-set/as-unqualified-kebab-maps})

(defn q
  [c sql]
  (jdbc/execute! c (sql/format sql) query-opts))
