(ns zdl.lex.server.auth
  (:require [clojure.set :as sets]
            [reitit.core :as r]
            [sieppari.context :as sieppari-context]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.server.auth.basic :as basic-auth]))

(def auth-user
  (getenv "SERVER_USER"))

(def auth-password
  (getenv "SERVER_PASSWORD"))

(defn authenticate
  [username password]
  (if-not (and auth-user auth-password)
    ;; pass-through credentials from upstream
    {:user username :password password}
    ;; else authenticate against env credentials
    (when (and (= auth-user username) (= auth-password password))
      {:user username :password password})))

(defn authenticated-request
  [request]
  (let [auth-req (basic-auth/basic-authentication-request request authenticate)
        {:keys [user] :as basic-auth} (:basic-authentication auth-req)]
    (cond-> request
      basic-auth (assoc ::identity
                        (assoc basic-auth :roles
                               (cond-> #{:user}
                                 (= "admin" user) (conj :admin)))))))

(defn required-roles
  [{:keys [request-method] ::r/keys [match]}]
  (or (get-in match [:data request-method ::roles])
      (get-in match [:data ::roles])))

(def realm
  "ZDL-Lex-Server")

(defn enter
  [{:keys [request] :as ctx}]
  (let [required (required-roles request)]
    (or
     (and (empty? required) ctx)
     (let [{::keys [identity] :as request} (authenticated-request request)
           {:keys [roles]}                 identity]
       (if (sets/subset? required roles)
         (assoc ctx :request request)
         (sieppari-context/terminate
          (assoc ctx :response (basic-auth/authentication-failure realm))))))))

(def interceptor
  {:name ::interceptor :enter enter})

(defn get-status
  [{::keys [identity]}]
  {:status 200 :body identity})
