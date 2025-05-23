(ns zdl.lex.server.h2
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as con]
            [next.jdbc.result-set :as result-set]
            [zdl.lex.fs :as fs]
            [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [clojure.tools.logging :as log])
  (:import com.zaxxer.hikari.HikariDataSource
           com.zaxxer.hikari.pool.HikariProxyConnection
           java.sql.Connection
           org.flywaydb.core.api.configuration.FluentConfiguration
           org.flywaydb.core.Flyway))

(defn h2-uri
  [path]
  (str (uri/map->URI {:scheme "jdbc:h2"
                      :path (str path ";TRACE_LEVEL_FILE=4")})))

(defn open!
  ^HikariDataSource [id path]
  (log/infof "Opening H2 database '%s' @ %s" id path)
  (let [uri (h2-uri (fs/path path))
        db  {:jdbcUrl uri :username "sa" :password ""}
        ds  (con/->pool HikariDataSource db)]
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
  [path]
  (doseq [suffix [".mv.db" ".trace.db"]]
    (fs/delete! (fs/file (str path suffix)) true)))

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

(defmethod print-method HikariDataSource
  [^HikariDataSource ds ^java.io.Writer writer]
  (.write writer (.getJdbcUrl ds)))

(defmethod print-method HikariProxyConnection
  [^HikariProxyConnection pc ^java.io.Writer writer]
  (print-method (.unwrap pc Connection) writer))

(defn execute!
  ([connectable sql]
   (execute! connectable sql {}))
  ([connectable sql opts]
   (log/debugf "%s: %s" (pr-str connectable) sql)
   (jdbc/execute! connectable (sql/format sql) opts)))

(def query-opts
  {:builder-fn result-set/as-unqualified-kebab-maps})

(defn query
  ([connectable sql]
   (query connectable sql {}))
  ([connectable sql opts]
   (execute! connectable sql (merge query-opts opts))))
