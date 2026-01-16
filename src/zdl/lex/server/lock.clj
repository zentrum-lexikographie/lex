(ns zdl.lex.server.lock
  (:require
   [pg.core :as pg]
   [pg.honey :as pgh]
   [ring.util.response :as resp]
   [zdl.lex.server.db :refer [db q]]
   [taoensso.telemere :as tm]))

(def ^:dynamic *context*
  nil)

(defn select-active-lock
  [c]
  (when-let [{:keys [resource owner token]} *context*]
    (first
     (q c {:select   :*
           :from     :lock
           :where    [:and
                      [:> :expires (System/currentTimeMillis)]
                      [:= :resource resource]
                      [:= :owner owner]
                      [:= :token token]]
           :order-by [:resource :owner :token]}))))

(defn select-other-locks
  [c]
  (when-let [{:keys [resource owner token]} *context*]
    (q c {:select   :*
          :from     :lock
          :where    [:and
                     [:> :expires (System/currentTimeMillis)]
                     [:= :resource resource]
                     [:or [:<> :owner owner] [:<> :token token]]]
          :order-by [:resource :owner :token]})))

(defn assert-unlocked
  [c]
  (when-let [other-lock (first (select-other-locks c))]
    (throw (ex-info "Locked" {:type ::locked
                              :lock other-lock}))))

(defn locked?
  [e]
  (some-> e ex-data :type (= ::locked)))

(def default-ttl
  (* 60 1000))

(def merge-sql-stmt
  (str "INSERT INTO lock (resource, owner, token, expires) "
       "VALUES ($1, $2, $3, $4) "
       "ON CONFLICT (resource, owner, token) "
       "DO UPDATE SET expires = EXCLUDED.expires"))

(defn merge-lock
  [c]
  (when-let [{:keys [resource owner token ttl] :or {ttl default-ttl}} *context*]
    (let [expires (+ (System/currentTimeMillis) ttl)]
      (pg/execute c merge-sql-stmt {:params [resource owner token expires]})
      {:resource resource :owner owner :token token :expires expires})))

(defn delete-lock
  [c]
  (when-let [{:keys [resource owner token]} *context*]
    (pgh/execute c {:delete-from :lock
                    :where       [:and
                                  [:> :expires (System/currentTimeMillis)]
                                  [:= :resource resource]
                                  [:= :owner owner]
                                  [:= :token token]]})
    *context*))

(defmacro with-lock
  [& forms]
  `(pg/with-transaction [tx# db {:isolation :serializable}]
     (assert-unlocked tx#)
     (let [active-lock# (select-active-lock tx#)]
       (try
         (when-not active-lock# (merge-lock tx#))
         ~@forms
         (finally
           (when-not active-lock# (delete-lock tx#)))))))

;; # HTTP API

(def context-middleware
  {:name ::middleware
   :wrap (fn [handler]
           (fn [{{owner :user}                :identity
                 {{:keys [resource]} :path}   :parameters
                 {{:keys [token ttl]} :query} :parameters
                 :as                          req}]
             (binding [*context* (cond-> {:owner    owner
                                          :resource resource
                                          :token    token}
                                   ttl (assoc :ttl (* ttl 1000)))]
               (tm/with-ctx+ {::context *context*}
                 (handler req)))))})

(defn response-not-found
  []
  (resp/not-found *context*))

(defn handle-read-locks
  [_]
  (pg/with-transaction [tx db {:read-only? true}]
    (resp/response
     (q tx {:select   [:resource :owner :expires]
            :from     :lock
            :where    [:> :expires (System/currentTimeMillis)]
            :order-by [:resource :owner :expires]}))))

(defn handle-read-lock
  [_req]
  (pg/with-transaction [tx db {:read-only? true}]
    (if-let [active (select-active-lock tx)]
      (resp/response active)
      (response-not-found))))

(defn handle-create-lock
  [_req]
  (pg/with-transaction [tx db {:isolation :serializable}]
    (if-let [other-lock (first (select-other-locks tx))]
      (-> other-lock (resp/response) (resp/status 423))
      (-> (merge-lock tx) (resp/response)))))

(defn handle-remove-lock
  [_req]
  (pg/with-transaction [tx db]
    (if (select-active-lock tx)
      (resp/response (delete-lock tx))
      (response-not-found))))

;; # Periodic Lock Cleanup

(defn cleanup!
  []
  (pg/with-transaction [tx db]
    (pgh/execute tx {:delete-from :lock
                     :where       [:<= :expires (System/currentTimeMillis)]})))
