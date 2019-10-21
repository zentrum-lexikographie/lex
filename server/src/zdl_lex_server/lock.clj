(ns zdl-lex-server.lock
  (:require [clojure.core.async :as a]
            [clojure.java.jdbc :as jdbc]
            [hugsql.core :refer [def-db-fns]]
            [me.raynes.fs :as fs]
            [mount.core :as mount :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.util :refer [uuid]]
            [zdl-lex-server.auth :as auth])
  (:import java.util.concurrent.locks.ReentrantReadWriteLock
           java.util.concurrent.TimeUnit))

(defonce global-lock (ReentrantReadWriteLock.))

(defmacro with-global-lock
  [lock-method & body]
  `(let [lock# (~lock-method ^ReentrantReadWriteLock global-lock)]
     (if (.tryLock lock# 30 TimeUnit/SECONDS)
       (try ~@body
            (finally
              (.unlock lock#)))
       (throw (ex-info "Storage lock timeout" {})))))

(defmacro with-global-read-lock [& body] `(with-global-lock .readLock ~@body))

(defmacro with-global-write-lock [& body] `(with-global-lock .writeLock ~@body))

(def-db-fns "zdl_lex_server/lock.sql")

(defstate ^{:on-reload :noop} db
  :start (let [db {:dbtype "h2"
                   :dbname (str (fs/file (env :data-dir) "locks"))
                   :user "sa"
                   :password ""}]
           (jdbc/with-db-connection [c db]
             (create-lock-table c)
             (create-lock-query-index c))
           db))

(defn cleanup-locks []
  (jdbc/with-db-transaction [c db]
    (remove-expired-locks c {:now (System/currentTimeMillis)})))

(defstate lock-cleanup-scheduler
  :start (cron/schedule "0 */5 * * * ?" "Lock cleanup" cleanup-locks)
  :stop (a/close! lock-cleanup-scheduler))

(defn- now []
  (System/currentTimeMillis))

(defn- lock [{{:keys [resource]} :path-params
              {:keys [ttl token] :or {ttl "60"}} :params
              owner :auth/user
              owner_ip :remote-addr
              :as req}]
  (when-not (not-empty resource)
    (throw (IllegalArgumentException. (str req))))
  (let [now (now)]
    {:resource resource
     :token (or token (uuid))
     :now now
     :expires (+ now (* 1000 (Integer/parseInt ttl)))
     :owner owner
     :owner_ip owner_ip}))

(defn get-list [_]
  (jdbc/with-db-transaction [c db {:read-only? true}]
    (htstatus/ok (list-active-locks db {:now (now)}))))

(defn get-lock [req]
  (let [lock (lock req)]
    (jdbc/with-db-transaction [c db {:read-only? true}]
      (if-let [active-lock (get-active-lock c lock)]
        (htstatus/ok lock)
        (htstatus/not-found)))))

(defn post-lock [req]
  (let [lock (lock req)]
    (jdbc/with-db-transaction [c db {:isolation :serializable}]
      (if-let [other-lock (get-other-lock c lock)]
        (htstatus/ok other-lock)
        (do (merge-lock c lock)
            (htstatus/ok (get-active-lock c lock)))))))

(defn delete-lock [req]
  (let [lock (lock req)]
    (jdbc/with-db-transaction [c db]
      (if-let [active-lock (get-active-lock c lock)]
        (do (remove-lock c (assoc active-lock :now (now)))
            (htstatus/ok active-lock))
        (htstatus/not-found lock)))))

(def ring-handlers
  ["/lock"
   ["" {:get get-list}]
   ["/*resource" {:get get-lock :post post-lock :delete delete-lock}]])

(comment
  (get-list {})
  (jdbc/with-db-transaction [c db]
    (merge-lock c {:resource "WDG/ab/Abenduniversitaet-E_a_421.xml",
                   :owner "admin",
                   :token "81184e67-2bbb-4531-a3c4-b11040250e94",
                   :owner_ip "127.0.0.1",
                   :expires 1579413482314
                   :now 1579413482313})))

