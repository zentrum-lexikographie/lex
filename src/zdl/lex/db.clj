(ns zdl.lex.db
  (:require
   [integrant.core :as ig]
   [pg.core :as pg]
   [pg.migration.core :as pmig]
   [pg.honey :as pgh]
   [zdl.lex.env :refer [getenv]]
   [taoensso.telemere :as tel]))

(def spec
  {:host            (getenv "DB_HOST" "localhost")
   :port            (parse-long (getenv "DB_PORT" "5432"))
   :database        (getenv "DB_NAME" "lex")
   :user            (getenv "DB_USER" "lex")
   :password        (getenv "DB_PASSWORD" "lex")
   :migrations-path "zdl/lex/db"
   :pool-max-size   8})

(def ^:dynamic db
  spec)

(defmethod ig/init-key ::pool
  [_ _]
  (tel/with-ctx+ {::db db}
    (tel/event! ::connect)
    (tel/with-streams->telemere (pmig/migrate-all spec))
    (alter-var-root #'db (constantly (pg/pool spec)))))

(defmethod ig/halt-key! ::pool
  [_ _]
  (alter-var-root #'db (constantly spec)))

(defn q
  [c sql]
  (pgh/query c sql {:honey {:inline true}}))
