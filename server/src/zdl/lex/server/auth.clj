(ns zdl.lex.server.auth
  (:require [buddy.auth.accessrules :refer [wrap-access-rules]]
            [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [zdl.lex.env :refer [env]]))

(def public?
  (constantly true))

(def admin?
  (comp some? #{"admin"} :user :identity))

(def authenticated?
  (comp some? :identity))

(def access-rules
  [
   ;; Public resources
   {:uris
    ["/"
     "/assets/*"
     "/docs/*"
     "/home"
     "/oxygen/*"
     "/swagger.json"
     "/zdl-lex-client/*"]
    :handler public?}
   ;; Admin resources
   {:uris
    ["/git"
     "/git/*"]
    :handler admin?}
   {:uris
    ["/index"
     "/mantis/issues"]
    :request-method :delete
    :handler admin?}
   ;; Authenticated resources
   {:uris
    ["/article/*"
     "/index"
     "/index/*"
     "/lock"
     "/lock/*"
     "/mantis/*"
     "/status"
     "/status/*"]
    :handler authenticated?}])

(defn authenticate
  [req {:keys [username password]}]
  (let [{:keys [server-user server-password]} env]
    (if-not (and server-user server-password)
      ;; pass-through credentials from upstream
      {:user username :password password}
      ;; else authenticate against env credentials
      (if (and (= server-user username) (= server-password password))
        {:user username :password password}))))

(def auth-backend
  (http-basic-backend {:realm "ZDL-Lex-Server" :authfn authenticate}))

(def wrap
  (comp
   #(wrap-authorization % auth-backend)
   #(wrap-authentication % auth-backend)
   #(wrap-access-rules % {:policy :reject :rules access-rules})))
