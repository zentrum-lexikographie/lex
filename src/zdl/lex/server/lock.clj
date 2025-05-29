(ns zdl.lex.server.lock
  (:require
   [next.jdbc :as jdbc]
   [ring.util.response :as resp]
   [zdl.lex.server.db :as db :refer [db]]))

(def ^:dynamic *context*
  nil)

(defn select-active-lock
  [c]
  (when-let [{:keys [resource owner token]} *context*]
    (first
     (db/query c {:select   :*
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
    (db/query c {:select   :*
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
       "VALUES (?, ?, ?, ?) "
       "ON CONFLICT (resource, owner, token) "
       "DO UPDATE SET expires = EXCLUDED.expires"))

(defn merge-lock
  [c]
  (when-let [{:keys [resource owner token ttl] :or {ttl default-ttl}} *context*]
    (let [expires (+ (System/currentTimeMillis) ttl)]
      (jdbc/execute! c [merge-sql-stmt resource  owner token expires])
      {:resource resource :owner owner :token token :expires expires})))

(defn delete-lock
  [c]
  (when-let [{:keys [resource owner token]} *context*]
    (db/execute! c {:delete-from :lock
                    :where       [:and
                                  [:> :expires (System/currentTimeMillis)]
                                  [:= :resource resource]
                                  [:= :owner owner]
                                  [:= :token token]]})
    *context*))

(defmacro with-lock
  [& forms]
  `(jdbc/with-transaction [c# db {:isolation :serializable}]
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
           (fn [{owner                        :zdl.lex.server.http/user
                 {{:keys [resource]} :path}   :parameters
                 {{:keys [token ttl]} :query} :parameters
                 :as                          req}]
             (binding [*context* (cond-> {:owner    owner
                                          :resource resource
                                          :token    token}
                                   ttl (assoc :ttl (* ttl 1000)))]
               (handler req))))})

(defn response-not-found
  []
  (resp/not-found *context*))

(defn handle-read-locks
  [_]
  (jdbc/with-transaction [c db {:read-only? true}]
    (resp/response
     (db/query c {:select   [:resource :owner :expires]
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
    (db/execute! c {:delete-from :lock
                    :where       [:<= :expires (System/currentTimeMillis)]})))
