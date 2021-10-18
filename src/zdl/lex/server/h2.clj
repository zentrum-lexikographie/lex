(ns zdl.lex.server.h2
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as con]
            [next.jdbc.result-set :as result-set]
            [zdl.lex.data :as data]
            [zdl.lex.fs :as fs]
            [clojure.string :as str])
  (:import com.zaxxer.hikari.HikariDataSource
           org.flywaydb.core.api.configuration.FluentConfiguration
           org.flywaydb.core.Flyway))

(def h2-spec
  {:dbtype   "h2"
   :username "sa"
   :password ""})

(defn ^HikariDataSource open!
  [id]
  (let [db (assoc h2-spec :dbname (fs/path (data/file id)))
        ds (con/->pool HikariDataSource db)]
    (-> (doto (FluentConfiguration.)
          (.baselineOnMigrate true)
          (.dataSource ds)
          (.table (str id "_schema"))
          (.locations
           (into-array [(str "zdl/lex/server/h2/migration/" id)])))
        (Flyway.)
        (.migrate))
    (jdbc/execute! ds [(format "SET CACHE_SIZE %d" (* 512 1024))])
    ds))

(defn close!
  [ds]
  (.close ^HikariDataSource ds))

(defn delete!
  [id]
  (doseq [suffix [".mv.db" ".trace.db"]]
    (fs/delete! (data/file (str id suffix)) true)))

(defn format-merge
  [k table]
  (if (sequential? table)
    (cond (map? (second table))
          (let [[table statement] table
                [table cols]
                (if (and (sequential? table) (sequential? (second table)))
                  table
                  [table])
                [sql & params] (sql/format-dsl statement)]
            (into [(str (sql/sql-kw k) " " (sql/format-entity table)
                        " "
                        (when (seq cols)
                          (str "("
                               (str/join ", " (map #'sql/format-entity cols))
                               ") "))
                        sql)]
                  params))
          (sequential? (second table))
          (let [[table cols] table]
            [(str (sql/sql-kw k) " " (sql/format-entity table)
                  " ("
                  (str/join ", " (map #'sql/format-entity cols))
                  ")")])
          :else
          [(str (sql/sql-kw k) " " (sql/format-entity table))])
    [(str (sql/sql-kw k) " " (sql/format-entity table))]))


(sql/register-clause! :merge-into format-merge :insert-into)

(defn execute!
  ([connectable sql]
   (execute! connectable sql {}))
  ([connectable sql opts]
   (jdbc/execute! connectable (sql/format sql) opts)))

(def query-opts
  {:builder-fn result-set/as-unqualified-kebab-maps})

(defn query
  ([connectable sql]
   (query connectable sql {}))
  ([connectable sql opts]
   (execute! connectable sql (merge query-opts opts))))
