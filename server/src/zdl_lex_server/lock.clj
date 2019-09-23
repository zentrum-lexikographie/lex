(ns zdl-lex-server.lock
  (:require [clojure.core.async :as a]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [hugsql.core :refer [def-db-fns]]
            [me.raynes.fs :as fs]
            [mount.core :as mount :refer [defstate]]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-server.store :as store]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [mount.core :as mount])
  (:import java.util.UUID))

(def-db-fns "zdl_lex_server/lock.sql")

(defn uuid []
  (-> (UUID/randomUUID) str str/lower-case))

(defstate db
  :start (let [db {:dbtype "h2"
                   :dbname (str (fs/file store/data-dir "locks"))
                   :user "sa"
                   :password ""}]
           (jdbc/with-db-connection [c db]
             (create-lock-table c)
             (create-lock-query-index c))
           db))

(defn cleanup-locks []
  (jdbc/with-db-transaction [c db]
    (remove-expired-locks c {:now (System/currentTimeMillis)})))

(defstate lock-cleanup
  :start (cron/schedule "0 */5 * * * ?" "Lock cleanup" cleanup-locks)
  :stop (a/close! lock-cleanup))

(comment
  (mount/start #'db #'lock-cleanup)
  (mount/stop))

(defn- now []
  (System/currentTimeMillis))

(defn- lock [req ttl]
  (let [components (str/split (get-in req [:path-params :path] "") #"/+")
        resource (some->> (drop-last components) (seq) (str/join \/))
        token (not-empty (last components))
        token (if (= "_" token) (uuid) token)
        now (now)]
    {:resource resource
     :token token
     :now now
     :expires (+ now (* 1000 ttl))
     :owner (:zdl-lex-server.http/user req)
     :owner_ip (:remote-addr req)}))

(defn get-list [_]
  (jdbc/with-db-transaction [c db {:read-only? true}]
    (htstatus/ok (list-active-locks db {:now (now)}))))

(defn post-lock [{{:keys [ttl] :or {ttl "60"}} :params :as req}]
  (let [lock (lock req (Integer/parseInt ttl))]
    (jdbc/with-db-transaction [c db {:isolation :serializable}]
      (if-let [other-lock (get-other-lock c lock)]
        (htstatus/bad-request other-lock)
        (do (merge-lock c lock) (htstatus/ok (get-active-lock c lock)))))))

(defn delete-lock [req]
  (let [lock (lock 0)]
    (jdbc/with-db-transaction [c db]
      (if-let [active-lock (get-active-lock c lock)]
        (do (remove-lock c active-lock) (htstatus/ok active-lock))
        (htstatus/not-found lock)))))

(def ring-handlers
  ["/lock"
   ["" {:get get-list}]
   ["/*path" {:post post-lock :delete delete-lock}]])
