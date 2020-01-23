(ns zdl-lex-server.auth
  (:require [clojure.data.codec.base64 :as base64]
            [clojure.string :as str]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.util.http-response :as htstatus]
            [zdl-lex-common.env :refer [env]]))

(defn wrap-authenticated [handler]
  (let [{:keys [http-anon-user server-user server-password]} env
        authenticated? (if (and server-user server-password)
                         #(if (and (= server-user %1) (= server-password %2))
                            [server-user server-password])
                         #(vector %1 %2))]
    (wrap-basic-authentication handler authenticated?)))


(defn- admin?
  [{:keys [basic-authentication]}]
  (= "admin" (first basic-authentication)))

(defn wrap-admin-only
  [handler]
  (fn
    ([request]
     (if-not (admin? request)
       (htstatus/forbidden)
       (handler request)))
    ([request respond raise]
     (if-not (admin? request)
       (respond (htstatus/forbidden))
       (handler request respond raise)))))
