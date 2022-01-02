w(ns zdl.lex.server.article.lock
  (:require
   [clojure.core.async :as a]
   [mount.core :as mount :refer [defstate]]
   [next.jdbc :as jdbc]
   [slingshot.slingshot :refer [throw+]]
   [zdl.lex.cron :as cron]
   [zdl.lex.server.auth :as auth]
   [zdl.lex.server.h2 :as h2]))

(defn- now
  []
  (System/currentTimeMillis))

(defn ttl->expires
  [ttl]
  (+ (now) (* 1000 ttl)))

;; # DB layer

(defstate db
  :start (h2/open! "locks")
  :stop  (h2/close! db))

(defn select-locks
  [c]
  (h2/query c {:select   :*
               :from     :lock
               :where    [:> :expires (now)]
               :order-by [:resource :owner :token]}))

(defn select-active-lock
  [c {:keys [resource owner token]}]
  (first
   (h2/query c {:select   :*
                :from     :lock
                :where    [:and
                           [:> :expires (now)]
                           [:= :resource resource]
                           [:= :owner owner]
                           [:= :token token]]
                :order-by [:resource :owner :token]})))

(defn select-other-locks
  [c {:keys [resource owner token]}]
  (h2/query c {:select   :*
               :from     :lock
               :where    [:and
                          [:> :expires (System/currentTimeMillis)]
                          [:= :resource resource]
                          [:or
                           [:<> :owner owner]
                           [:<> :token token]]]
               :order-by [:resource :owner :token]}))

(defn assert-unlocked
  [c lock]
  (when-let [other-lock (first (select-other-locks c lock))]
    (throw+ {:type       ::locked
             :lock       lock
             :other-lock other-lock})))

(defn merge-lock
  [c {:keys [resource owner token expires]}]
  (h2/execute! c {:merge-into :lock
                  :columns    [:resource :owner :token :expires]
                  :values     [[resource owner token expires]]}))

(defn delete-lock
  [c lock]
  (h2/execute! c {:delete-from :lock
                  :where       [:and
                                [:> :expires (now)]
                                [:= :resource (:resource lock)]
                                [:= :owner (:owner lock)]
                                [:= :token (:token lock)]]}))

;; # HTTP API

(defn request->lock
  [{{owner :user} ::auth/identity :as req}]
  (let [parameters                       (:parameters req)
        {:keys [resource]}               (:path parameters)
        {:keys [ttl token] :or {ttl 60}} (:query parameters)]
    {:resource resource
     :token    token
     :owner    owner
     :expires  (ttl->expires ttl)}))

(defn- lock->response
  [lock & {:keys [token?] :or {token? false}}]
  (->> [:resource :owner :expires]
       (concat (when token? [:token]))
       (select-keys lock)))

(defn read-locks
  [_]
  (jdbc/with-transaction [c db {:read-only? true}]
    {:status 200 :body (map lock->response (select-locks c))}))

(defn read-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-transaction [c db {:read-only? true}]
      (if-let [active (select-active-lock c lock)]
        {:status 200 :body (lock->response active)}
        {:status 404 :body (lock->response lock)}))))

(defn create-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-transaction [c db {:isolation :serializable}]
      (if-let [other-lock (first (select-other-locks c lock))]
        {:status 423 :body (lock->response other-lock)}
        (do (merge-lock c lock)
            {:status 200 :body (lock->response lock :token? true)})))))

(defn remove-lock
  [req]
  (let [lock (request->lock req)]
    (jdbc/with-transaction [c db]
      (if-let [lock (select-active-lock c lock)]
        (do
          (delete-lock c lock)
          {:status 200 :body (lock->response lock :token? true)})
        {:status 404 :body lock}))))

;; # Periodic Lock Cleanup

(defn cleanup!
  []
  (jdbc/with-transaction [c db]
    (h2/execute! c {:delete-from :lock
                    :where       [:<= :expires (now)]})))

(defstate cleanup
  :start (cron/schedule "0 */5 * * * ?" "Lock cleanup" cleanup!)
  :stop (a/close! cleanup))

;; # Locked Editing support

(defn editor
  [lock editor']
  (fn [f]
    (jdbc/with-transaction [c db {:isolation :serializable}]
      (assert-unlocked c lock)
      (let [active-lock (select-active-lock c lock)]
        (try
          (when-not active-lock (merge-lock c lock))
          (editor' f)
          (finally
            (when-not active-lock (delete-lock c lock))))))))
