(ns zdl.lex.server.lock
  (:require
   [next.jdbc :as jdbc]
   [ring.util.response :as resp]
   [taoensso.telemere :as tm]
   [zdl.lex.server.db :refer [q]]))

(def ^:dynamic *context*
  nil)

(defn select-active-lock
  [c]
  (when-let [{:keys [resource owner token]} *context*]
    (first
     (q c {:select   :*
           :from     :resource-lock
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
          :from     :resource-lock
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
  (str "INSERT INTO resource_lock (resource, owner, token, expires) "
       "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE expires = ?"))

(defn merge-lock
  [c]
  (when-let [{:keys [resource owner token ttl] :or {ttl default-ttl}} *context*]
    (let [expires (+ (System/currentTimeMillis) ttl)]
      (jdbc/execute! c [merge-sql-stmt resource owner token expires expires])
      {:resource resource :owner owner :token token :expires expires})))

(defn delete-lock
  [c]
  (when-let [{:keys [resource owner token]} *context*]
    (q c {:delete-from :resource-lock
          :where       [:and
                        [:> :expires (System/currentTimeMillis)]
                        [:= :resource resource]
                        [:= :owner owner]
                        [:= :token token]]})
    *context*))

(defn with-lock
  [db f]
  (jdbc/with-transaction [tx db {:isolation :serializable}]
    (assert-unlocked tx)
    (let [active-lock# (select-active-lock tx)]
      (try
        (when-not active-lock# (merge-lock tx))
        (f)
        (finally
          (when-not active-lock# (delete-lock tx)))))))

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
  [db _]
  (jdbc/with-transaction [tx db {:read-only? true}]
    (resp/response
     (q tx {:select   [:resource :owner :expires]
            :from     :resource-lock
            :where    [:> :expires (System/currentTimeMillis)]
            :order-by [:resource :owner :expires]}))))

(defn handle-read-lock
  [db _req]
  (jdbc/with-transaction [tx db {:read-only? true}]
    (if-let [active (select-active-lock tx)]
      (resp/response active)
      (response-not-found))))

(defn handle-create-lock
  [db _req]
  (jdbc/with-transaction [tx db {:isolation :serializable}]
    (if-let [other-lock (first (select-other-locks tx))]
      (-> other-lock (resp/response) (resp/status 423))
      (-> (merge-lock tx) (resp/response)))))

(defn handle-remove-lock
  [db _req]
  (jdbc/with-transaction [tx db]
    (if (select-active-lock tx)
      (resp/response (delete-lock tx))
      (response-not-found))))

;; # Periodic Lock Cleanup

(defn cleanup!
  [db]
  (jdbc/with-transaction [tx db]
    (q tx {:delete-from :resource-lock
           :where       [:<= :expires (System/currentTimeMillis)]})))

(defn handlers
  [db]
  [""
   [""
    {:summary "Retrieve list of active locks"
     :tags    ["Lock" "Query"]
     :handler (partial handle-read-locks db)}]
   ["/*resource"
    {:get    {:summary    "Read a resource lock"
              :tags       ["Lock" "Query" "Resource"]
              :parameters {:path  [:map [:resource :string]]
                           :query [:map [:token :string]]}
              :handler (partial handle-read-lock db)}
     :post   {:summary    "Set a resource lock"
              :tags       ["Lock" "Resource"]
              :parameters {:path  [:map [:resource :string]]
                           :query [:map
                                   [:token :string]
                                   [:ttl [:int {:min 1}]]]}
              :handler (partial handle-create-lock db)}
     :delete {:summary    "Remove a resource lock."
              :tags       ["Lock" "Resource"]
              :parameters {:path  [:map [:resource :string]]
                           :query [:map [:token :string]]}
              :handler (partial handle-remove-lock db)}}]])
