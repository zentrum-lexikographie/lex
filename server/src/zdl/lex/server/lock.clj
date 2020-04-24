(ns zdl.lex.server.lock
  (:require [clojure.core.async :as a]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [hugsql.core :refer [def-db-fns]]
            [mount.core :as mount :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [zdl.lex.cron :as cron]
            [zdl.lex.fs :as fs]
            [zdl.lex.spec :as spec]
            [zdl.lex.util :refer [fpath]])
  (:import java.net.URI
           org.h2.jdbcx.JdbcConnectionPool))

(defn- now []
  (System/currentTimeMillis))

(def-db-fns "zdl/lex/server/lock.sql")

(defstate datasource
  :start
  (-> (URI. "jdbc:h2" (fpath (fs/data-file "locks")) nil)
      (str)
      (JdbcConnectionPool/create "sa" ""))
  :stop (.dispose datasource))

(defstate db
  :start (let [db {:datasource datasource}]
           (jdbc/with-db-connection [c db]
             (create-lock-table c)
             (create-lock-query-index c)
             db)))

(defn cleanup-locks []
  (jdbc/with-db-transaction [c db]
    (delete-expired-locks c {:now (System/currentTimeMillis)})))

(defstate lock-cleanup-scheduler
  :start (cron/schedule "0 */5 * * * ?" "Lock cleanup" cleanup-locks)
  :stop (a/close! lock-cleanup-scheduler))

(defn- resource->paths
  [resource]
  (let [components (vec (str/split resource #"/"))
        paths (for [i (range (-> components count inc))] (subvec components 0 i))
        paths (map (partial str/join "/") paths)]
    (apply sorted-set paths)))

(defn- request->lock
  [{{owner :user} :identity owner_ip :remote-addr :as req}]
  (let [parameters (:parameters req)
        {:keys [resource]} (:path parameters)
        {:keys [ttl token] :or {ttl 60}} (:query parameters)
        paths (resource->paths resource)
        now (now)
        expires (+ now (* 1000 ttl))]
    {:resource resource :paths paths
     :token token :owner owner :owner_ip owner_ip
     :now now :expires expires}))

(defn- lock->response
  [lock & {:keys [token?] :or {token? false}}]
  (->> [:resource :owner :owner_ip :expires]
       (concat (if token? [:token]))
       (select-keys lock)))

(defn locked-by-other?
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-db-transaction [c db {:read-only? true}]
      (let [locks (select-other-locks c lock)]
        (some-> locks last lock->response)))))

(defn wrap-resource-lock
  [handler]
  (fn
    ([request]
     (if-let [lock (locked-by-other? request)]
       (htstatus/locked lock)
       (handler request)))
    ([request respond raise]
     (if-let [lock (locked-by-other? request)]
       (respond (htstatus/locked lock))
       (handler request respond raise)))))

(defn read-locks [_]
  (jdbc/with-db-transaction [c db {:read-only? true}]
    (let [locks (select-locks db {:now (now)})
          locks (map lock->response locks)]
      (htstatus/ok locks))))

(defn read-lock [req]
  (let [lock (request->lock req)]
    (jdbc/with-db-transaction [c db {:read-only? true}]
      (if-let [active (last (select-active-locks c lock))]
        (htstatus/ok (lock->response active))
        (htstatus/not-found (lock->response lock))))))

(defn create-lock [req]
  (let [lock (request->lock req)]
    (jdbc/with-db-transaction [c db {:isolation :serializable}]
      (if-let [other-lock (last (select-other-locks c lock))]
        (htstatus/locked (lock->response other-lock))
        (do (merge-lock c lock)
            (htstatus/ok (lock->response lock :token? true)))))))

(defn remove-lock [req]
  (let [lock (request->lock req)]
    (jdbc/with-db-transaction [c db]
      (if-let [lock (select-active-lock c lock)]
        (let [lock (assoc lock :now (now))]
          (delete-lock c lock)
          (htstatus/ok (lock->response lock :token? true)))
        (htstatus/not-found lock)))))

(s/def ::resource string?)
(s/def ::token string?)
(s/def ::ttl ::spec/pos-int)

(def new-lock-parameters
  {:path (s/keys :req-un [::resource])
   :query (s/keys :opt-un [::token ::ttl])})

(def existing-lock-parameters
  {:path (s/keys :req-un [::resource])
   :query (s/keys :req-un [::token])})

(def read-locks-handler
  {:summary "Retrieve list of active locks"
   :tags ["Lock" "Query"]
   :handler read-locks})

(def read-lock-handler
  {:summary "Read a resource lock"
   :tags ["Lock" "Query" "Resource"]
   :parameters existing-lock-parameters
   :handler read-lock})

(def ring-handlers
  ["/lock"
   [""
    {:get read-locks-handler
     :head read-locks-handler}]
   ["/*resource"
    {:get read-lock-handler
     :head read-lock-handler
     :post {:summary "Set a resource lock"
            :tags ["Lock" "Resource"]
            :parameters new-lock-parameters
            :handler create-lock}
     :delete {:summary "Remove a resource lock."
              :tags ["Lock" "Resource"]
              :parameters existing-lock-parameters
              :handler remove-lock}}]])

(comment
  (read-locks {})
  (jdbc/with-db-transaction [c db]
    (let [now (now)]
      (merge-lock c {:resource "",
                     :owner "admin",
                     :token "81184e67-2bbb-4531-a3c4-b11040250e94",
                     :owner_ip "127.0.0.1",
                     :expires (+ now 300000)
                     :now now}))))

