(ns zdl.lex.server.auth
  (:require [buddy.auth.accessrules :refer [wrap-access-rules]]
            [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [zdl.lex.env :refer [getenv]]))

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
     "/cli/*"
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

(def authenticate
  (delay
    (let [auth-user (getenv "ZDL_LEX_SERVER_USER")
          auth-password (getenv "ZDL_LEX_SERVER_PASSWORD")]
      (fn [req {:keys [username password]}]
        (if-not (and auth-user auth-password)
          ;; pass-through credentials from upstream
          {:user username :password password}
          ;; else authenticate against env credentials
          (if (and (= auth-user username) (= auth-password password))
            {:user username :password password}))))))

(defn wrap
  [handler]
  (let [auth-backend (http-basic-backend {:realm "ZDL-Lex-Server"
                                          :authfn @authenticate})]
    (-> handler
        (wrap-access-rules {:policy :reject :rules access-rules})
        (wrap-authentication auth-backend)
        (wrap-authorization auth-backend))))
