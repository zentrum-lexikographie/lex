(ns zdl.lex.server.lock
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [hugsql.core :refer [def-db-fns]]
            [mount.core :as mount :refer [defstate]]
            [zdl.lex.server.auth :as auth]
            [zdl.lex.server.h2 :as h2]))

(defn- now []
  (System/currentTimeMillis))

(def-db-fns "zdl/lex/server/lock.sql")

(defstate db
  :start (let [db (h2/open! "locks")]
           (jdbc/with-db-connection [c db]
             (create-lock-table c)
             (create-lock-query-index c)
             db))
  :stop (h2/close! db))

(defn cleanup
  []
  (jdbc/with-db-transaction [c db]
    (delete-expired-locks c {:now (System/currentTimeMillis)})))

(defn- resource->paths
  [resource]
  (let [components (vec (str/split resource #"/"))
        paths      (for [i (range (-> components count inc))]
                     (subvec components 0 i))
        paths      (map (partial str/join "/") paths)]
    (apply sorted-set paths)))

(defn- request->lock
  [{{owner :user} ::auth/identity owner_ip :remote-addr :as req}]
  (let [parameters                       (:parameters req)
        {:keys [resource]}               (:path parameters)
        {:keys [ttl token] :or {ttl 60}} (:query parameters)
        paths                            (resource->paths resource)
        now                              (now)
        expires                          (+ now (* 1000 ttl))]
    {:resource resource :paths   paths
     :token    token    :owner   owner :owner_ip owner_ip
     :now      now      :expires expires}))

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
       {:status 423 :body lock}
       (handler request)))
    ([request respond raise]
     (if-let [lock (locked-by-other? request)]
       (respond {:status 423 :body lock})
       (handler request respond raise)))))

(defn read-locks
  [_]
  (jdbc/with-db-transaction [c db {:read-only? true}]
    (let [locks (select-locks db {:now (now)})
          locks (map lock->response locks)]
      {:status 200 :body locks})))

(defn read-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-db-transaction [c db {:read-only? true}]
      (if-let [active (last (select-active-locks c lock))]
        {:status 200 :body (lock->response active)}
        {:status 404 :body (lock->response lock)}))))

(defn create-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-db-transaction [c db {:isolation :serializable}]
      (if-let [other-lock (last (select-other-locks c lock))]
        {:status 423 :body (lock->response other-lock)}
        (do (merge-lock c lock)
            {:status 200 :body (lock->response lock :token? true)})))))

(defn remove-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-db-transaction [c db]
      (if-let [lock (select-active-lock c lock)]
        (let [lock (assoc lock :now (now))]
          (delete-lock c lock)
          {:status 200 :body (lock->response lock :token? true)})
        {:status 404 :body lock}))))

(comment
  (read-locks {})
  (jdbc/with-db-transaction [c db]
    (let [now (now)]
      (merge-lock c {:resource "",
                     :owner    "admin",
                     :token    "81184e67-2bbb-4531-a3c4-b11040250e94",
                     :owner_ip "127.0.0.1",
                     :expires  (+ now 300000)
                     :now      now}))))

