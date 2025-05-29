(ns zdl.lex.server.lock
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [honey.sql :as sql]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as jdbc.con]
   [next.jdbc.result-set :as jdbc.result-set]
   [ring.util.response :as resp]
   [taoensso.telemere :as t]
   [zdl.lex.env :as env])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(def ttl
  (* 60 1000))

(def ^:dynamic *token*
  nil)

(def ^:dynamic *owner*
  nil)

(def ^:dynamic *resource*
  nil)

;; # DB layer

(defn execute!
  ([connectable sql]
   (execute! connectable sql {}))
  ([connectable sql opts]
   (jdbc/execute! connectable (sql/format sql) opts)))

(def query-opts
  {:builder-fn-fn jdbc.result-set/as-unqualified-kebab-maps})

(defn query
  ([connectable sql]
   (query connectable sql {}))
  ([connectable sql opts]
   (execute! connectable sql (merge query-opts opts))))

(defn format-merge
  [k table]
  (if (sequential? table)
    (cond (map? (second table))
          (let [[table statement] table
                [table cols]
                (if (and (sequential? table) (sequential? (second table)))
                  table
                  [table])
                [sql & params]    (sql/format-dsl statement)]
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

(def ^:dynamic db
  nil)

(def ddl
  [(str "create table if not exists lock ("
        "resource varchar(255) not null,"
        "owner varchar(64) not null,"
        "token varchar(36) not null,"
        "expires bigint not null,"
        "primary key (resource, owner, token))")
   (str "create index if not exists lock_query_index "
        "on lock (expires, resource, owner, token)")
   (str "set cache_size " (* 512 1024))])

(defn init-db!
  [^HikariDataSource ds]
  (doseq [stmt ddl] (jdbc/execute! ds [stmt]))
  ds)

(defn close-db
  []
  (when db (.close db) (alter-var-root #'db (constantly nil))))

(defn open-db
  []
  (close-db)
  (-> env/lock-db ::env/lock-db-path fs/parent fs/create-dirs)
  (t/log! :info (format "Open %s" (:jdbcUrl env/lock-db)))
  (->> (jdbc.con/->pool HikariDataSource env/lock-db)
       (init-db!)
       (constantly)
       (alter-var-root #'db)))

(defn select-active-lock
  [c]
  (first
   (query c {:select   :*
             :from     :lock
             :where    [:and
                        [:> :expires (System/currentTimeMillis)]
                        [:= :resource *resource*]
                        [:= :owner *owner*]
                        [:= :token *token*]]
             :order-by [:resource :owner :token]})))

(defn select-other-locks
  [c]
  (query c {:select   :*
            :from     :lock
            :where    [:and
                       [:> :expires (System/currentTimeMillis)]
                       [:= :resource *resource*]
                       [:or
                        [:<> :owner *owner*]
                        [:<> :token *token*]]]
            :order-by [:resource :owner :token]}))

(defn assert-unlocked
  [c]
  (when-let [other-lock (first (select-other-locks c))]
    (throw (ex-info "Locked" {:type ::locked
                              :lock other-lock}))))

(defn locked?
  [e]
  (some-> e ex-data :type (= ::locked)))

(defn merge-lock
  [c]
  (let [expires (+ (System/currentTimeMillis) ttl)]
    (execute! c {:merge-into :lock
                 :columns    [:resource :owner :token :expires]
                 :values     [[*resource* *owner* *token* expires]]})
    {:resource *resource* :owner *owner* :token *token* :expires expires}))

(defn delete-lock
  [c]
  (execute! c {:delete-from :lock
               :where       [:and
                             [:> :expires (System/currentTimeMillis)]
                             [:= :resource *resource*]
                             [:= :owner *owner*]
                             [:= :token *token*]]})
  {:resource *resource* :owner *owner* :token *token*})

(defmacro with-lock
  [& forms]
  `(jdbc/with-transaction [c# ~db {:isolation :serializable}]
     (assert-unlocked c#)
     (let [active-lock# (select-active-lock c#)]
       (try
         (when-not active-lock# (merge-lock c#))
         ~@forms
         (finally
           (when-not active-lock# (delete-lock c#)))))))

;; # HTTP API

(def context-middleware
  {:name ::middleware
   :wrap (fn [handler]
           (fn [{user :zdl.lex.server.http/user params :parameters :as req}]
             (binding [*owner*    user
                       *resource* (get-in params [:path :resource])
                       *token*    (get-in params [:query :token])]
               (handler req))))})

(defn response-not-found
  []
  (resp/not-found {:resource *resource* :owner *owner* :token *token*}))

(defn handle-read-locks
  [_]
  (jdbc/with-transaction [c db {:read-only? true}]
    (resp/response
     (query c {:select   [:resource :owner :expires]
               :from     :lock
               :where    [:> :expires (System/currentTimeMillis)]
               :order-by [:resource :owner :expires]}))))

(defn handle-read-lock
  [_req]
  (jdbc/with-transaction [c db {:read-only? true}]
    (if-let [active (select-active-lock c)]
      (resp/response active)
      (response-not-found))))

(defn handle-create-lock
  [_req]
  (jdbc/with-transaction [c db {:isolation :serializable}]
    (if-let [other-lock (first (select-other-locks c))]
      (-> other-lock (resp/response) (resp/status 423))
      (-> (merge-lock c) (resp/response)))))

(defn handle-remove-lock
  [_req]
  (jdbc/with-transaction [c db]
    (if (select-active-lock c)
      (resp/response (delete-lock c))
      (response-not-found))))

;; # Periodic Lock Cleanup

(defn cleanup!
  []
  (jdbc/with-transaction [c db]
    (execute! c {:delete-from :lock
                 :where       [:<= :expires (System/currentTimeMillis)]})))
