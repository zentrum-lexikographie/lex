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
    (delete-expired-locks c {:now (System/currentTimeMillis)})))

(defstate lock-cleanup
  :start (cron/schedule "0 */5 * * * ?" "Lock cleanup" cleanup-locks)
  :stop (a/close! lock-cleanup))

(comment
  (mount/start #'db #'lock-cleanup)
  (mount/stop))

(let [timestamps #(let [now (System/currentTimeMillis)]
                    (hash-map :now now :expires (+ now (* 1000 %))))
      owner #(hash-map :owner (:zdl-lex-server.http/user %)
                       :owner_ip (:remote-addr %))
      id #(let [components (str/split (get-in % [:path-params :path] "") #"/+")
                resource (some->> (drop-last components) (seq) (str/join \/))
                token (not-empty (last components))
                token (if (= "_" token) (uuid) token)]
            (hash-map :resource resource :token token))]
  (def ring-handlers
    ["/lock"
     [""
      {:get
       (fn
         [_]
         (jdbc/with-db-transaction [c db {:read-only? true}]
           (->> (timestamps 0) (list-active-locks db) (htstatus/ok))))}]
     ["/*path"
      {:post
       (fn
         [{{:keys [ttl] :or {ttl "60"}} :params :as req}]
         (let [lock (merge (id req)
                           (owner req)
                           (timestamps (Integer/parseInt ttl)))]
           (jdbc/with-db-transaction [c db {:isolation :serializable}]
             (if-let [other-lock (get-other-lock c lock)]
               (htstatus/bad-request other-lock)
               (do (merge-lock c lock)
                   (htstatus/ok (get-active-lock c lock)))))))
       :delete
       (fn [req]
         (let [lock (merge (id req) (timestamps 0) (owner req))]
           (jdbc/with-db-transaction [c db]
             (if-let [lock (get-active-lock c lock)]
               (do (delete-lock c lock) (htstatus/ok id))
               (htstatus/not-found)))))}]]))
