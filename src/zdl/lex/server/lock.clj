(ns zdl.lex.server.lock
  (:require [clojure.string :as str]
            [clojure.core.async :as a]
            [mount.core :as mount :refer [defstate]]
            [next.jdbc :as jdbc]
            [zdl.lex.cron :as cron]
            [zdl.lex.server.auth :as auth]
            [zdl.lex.server.h2 :as h2]))

(defn- now
  []
  (System/currentTimeMillis))

(defstate db
  :start (h2/open! "locks")
  :stop  (h2/close! db))

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
    {:resource resource
     :paths    paths
     :token    token
     :owner    owner
     :owner_ip owner_ip
     :expires  expires}))

(defn- lock->response
  [lock & {:keys [token?] :or {token? false}}]
  (->> [:resource :owner :owner_ip :expires]
       (concat (when token? [:token]))
       (select-keys lock)))

(defn select-other-locks
  [c lock]
  (h2/query c {:select   :*
               :from     :lock
               :where    [:and
                          [:> :expires (System/currentTimeMillis)]
                          [:in :resource (:paths lock)]
                          [:or
                           [:<> :owner (:owner lock)]
                           [:<> :token (:token lock)]]]
               :order-by [:resource :owner :token]}))

(defn locked-by-other?
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-transaction [c db {:read-only? true}]
      (some->> (select-other-locks c lock) last lock->response))))

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

(defn select-locks
  [c]
  (h2/query c {:select   :*
               :from     :lock
               :where    [:> :expires (now)]
               :order-by [:resource :owner :token]}))

(defn read-locks
  [_]
  (jdbc/with-transaction [c db {:read-only? true}]
    {:status 200 :body (map lock->response (select-locks c))}))

(defn select-active-locks
  [c lock]
  (h2/query c {:select   :*
               :from     :lock
               :where    [:and
                          [:> :expires (now)]
                          [:in :resource (:paths lock)]]
               :order-by [:resource :owner :token]}))

(defn read-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-transaction [c db {:read-only? true}]
      (if-let [active (last (select-active-locks c lock))]
        {:status 200 :body (lock->response active)}
        {:status 404 :body (lock->response lock)}))))

(defn merge-lock
  [c {:keys [resource owner token owner_ip expires]}]
  (h2/execute! c {:merge-into :lock
                  :columns    [:resource :owner :token :owner_ip :expires]
                  :values     [[resource owner token owner_ip expires]]}))

(defn create-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-transaction [c db {:isolation :serializable}]
      (if-let [other-lock (last (select-other-locks c lock))]
        {:status 423 :body (lock->response other-lock)}
        (do (merge-lock c lock)
            {:status 200 :body (lock->response lock :token? true)})))))

(defn select-active-lock
  [c lock]
  (first
   (h2/query c {:select   :*
                :from     :lock
                :where    [:and
                           [:> :expires (now)]
                           [:= :resource (:resource lock)]
                           [:= :owner (:owner lock)]
                           [:= :token (:token lock)]]
                :order-by [:resource :owner :token]})))

(defn delete-lock
  [c lock]
  (h2/execute! c {:delete-from :lock
                  :where       [:and
                                [:> :expires (now)]
                                [:= :resource (:resource lock)]
                                [:= :owner (:owner lock)]
                                [:= :token (:token lock)]]}))

(defn remove-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-transaction [c db]
      (if-let [lock (select-active-lock c lock)]
        (do
          (delete-lock c lock)
          {:status 200 :body (lock->response lock :token? true)})
        {:status 404 :body lock}))))

(defn cleanup!
  []
  (jdbc/with-transaction [c db]
    (h2/execute! c {:delete-from :lock
                    :where       [:<= :expires (now)]})))

(defstate cleanup
  :start (cron/schedule "0 */5 * * * ?" "Lock cleanup" cleanup!)
  :stop (a/close! cleanup))

(comment
  (h2/delete! "locks")
  (mount/start #'db)
  (mount/stop)
  (read-locks {})
  (cleanup)
  (jdbc/with-transaction [c db]
    (let [now (now)]
      (merge-lock c {:resource "/test",
                     :owner    "admin",
                     :token    "81184e67-2bbb-4531-a3c4-b11040250e94",
                     :owner_ip "127.0.0.1",
                     :expires  (+ now 300000)}))))
