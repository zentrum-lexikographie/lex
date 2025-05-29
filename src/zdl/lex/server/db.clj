(ns zdl.lex.server.db
  (:require
   [honey.sql :as sql]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as jdbc.con]
   [next.jdbc.result-set :as jdbc.result-set]
   [ragtime.next-jdbc :as rg]
   [ragtime.repl :as rgr]
   [taoensso.telemere :as tm]
   [zdl.lex.env :as env]
   [zdl.lex.server.ddc :as ddc])
  (:import
   (com.pgvector PGvector)
   (com.zaxxer.hikari HikariDataSource)))

(require 'next.jdbc.date-time)

(defn init-db!
  [db]
  (rgr/migrate
   {:datastore  (rg/sql-database db {:migrations-table "migrations"})
    :migrations (rg/load-resources "zdl/lex/server/db")
    :reporter   (fn [_ _op id]
                  (tm/event! ::migrate {:level     :debug
                                       :msg       (format "%20s: Migrating" id)
                                        :migration id}))})
  db)

(def ^:dynamic db
  nil)

(defn close-db
  []
  (when db (.close db) (alter-var-root #'db (constantly nil))))

(defn open-db
  []
  (close-db)
  (->> (jdbc.con/->pool HikariDataSource env/db)
       (init-db!)
       (constantly)
       (alter-var-root #'db)))

(defn execute!
  ([sql]
   (execute! db sql))
  ([connectable sql]
   (execute! connectable sql {}))
  ([connectable sql opts]
   (jdbc/execute! connectable (sql/format sql) opts)))

(def query-opts
  {:builder-fn jdbc.result-set/as-unqualified-kebab-maps})

(defn query
  ([sql]
   (query db sql {}))
  ([connectable sql]
   (query connectable sql {}))
  ([connectable sql opts]
   (execute! connectable sql (merge query-opts opts))))


(defn import-lexemes!
  []
  (->>
   (map #(get % "form_s") [] #_(lex/minimal)) ;; FIXME!
   (partition-all 1000)
   (mapcat ddc/ppms)
   (into [] (take 3000))
   (sort-by (comp - second))))

(comment
  (tm/with-min-level :debug (tm/with-signals (init-db! db)))
  (execute! ["SELECT * FROM lexeme"])
  (execute! ["CREATE TABLE items (id bigserial PRIMARY KEY, embedding vector(3))"])
  (jdbc/with-transaction [tx db]
    (PGvector/registerTypes tx)
    (jdbc/execute! tx ["INSERT INTO items (embedding) VALUES (?)"
                      (PGvector. (float-array [1 1 1]))]))
  (jdbc/with-transaction [tx db]
    (PGvector/registerTypes tx)
    (jdbc/execute! tx ["SELECT * FROM items ORDER BY embedding <-> ? LIMIT 5"
                       (PGvector. (float-array [1 1 1]))])))
