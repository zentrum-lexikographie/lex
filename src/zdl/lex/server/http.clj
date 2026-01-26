(ns zdl.lex.server.http
  (:require
   [buddy.auth.accessrules]
   [buddy.auth.backends]
   [buddy.auth.middleware]
   [integrant.core :as ig]
   [muuntaja.core :as m]
   [org.httpkit.server :as http-kit]
   [reitit.coercion.malli]
   [reitit.ring]
   [reitit.ring.coercion]
   [reitit.ring.middleware.exception]
   [reitit.ring.middleware.muuntaja]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.middleware.defaults]
   [ring.util.response :as resp]
   [taoensso.telemere :as tm]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.html :as html]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.lock :as lock]
   [zdl.lex.server.oxygen :as oxygen]
   [zdl.lex.server.schedule :as schedule]
   [zdl.lex.server.socket :as server.socket]))

(def handler-defaults
  (-> ring.middleware.defaults/site-defaults
      (assoc-in [:proxy] true)
      (assoc-in [:security :anti-forgery] false)))

(def defaults-middleware
  {:name ::defaults
   :wrap #(ring.middleware.defaults/wrap-defaults % handler-defaults)})

(defn log-exceptions
  [handler ^Throwable e request]
  (when-not (some-> e ex-data :type #{:reitit.ring/response}) (tm/error! e))
  (handler e request))

(def exception-middleware
  (-> reitit.ring.middleware.exception/default-handlers
      (assoc :reitit.ring.middleware.exception/wrap log-exceptions)
      (reitit.ring.middleware.exception/create-exception-middleware)))

(defn handle-unauthorized
  [request {:keys [realm] :as _auth-data}]
  (if (:identity request)
    (-> (resp/response "Permission denied")
        (resp/status 403))
    (-> (resp/response "Unauthorized")
        (resp/header "WWW-Authenticate" (format "Basic realm=\"%s\"" realm))
        (resp/status 401))))

(defn auth-backend
  [userbase]
  (buddy.auth.backends/basic
   {:realm                "ZDL-Lex-Server"
    :authfn               (fn [_request {:keys [username password] :as _auth}]
                            (get userbase [username password]))
    :unauthorized-handler handle-unauthorized}))

(def access-rules
  (letfn [(authenticated? [{id :identity :as _req}] (some? id))
          (admin? [{{:keys [user]} :identity :as _req}] (= "admin" user))
          (public? [_req] true)]
    {:rules [{:pattern #"^/git.*" :handler authenticated?}
             {:pattern #"^/socket.*" :handler authenticated?}
             {:pattern #"^/index.*" :handler authenticated?}
             {:pattern #"^/lock.*" :handler authenticated?}
             {:pattern #"^/schedule.*" :handler admin?}
             {:pattern #"^/.*" :handler public?}]}))

(def auth-context-middleware
  {:name ::auth-context-middleware
   :wrap (fn [handler]
           (fn [{{:keys [user]} :identity :as req}]
             (cond-> (handler req) user (resp/header "X-Lex-User" user))))})

(def html-handlers
  [""
   ["/"
    (constantly (-> (html/install "/")
                    (resp/response)
                    (resp/content-type "text/html")))]
   ["/styles.css"
    (constantly (-> html/css (resp/response) (resp/content-type "text/css")))]])

(def swagger-handlers
  [""
   ["/docs/api/*"
    {:no-doc  true
     :handler (swagger-ui/create-swagger-ui-handler)}]
   ["/swagger.json"
    {:no-doc  true
     :handler (swagger/create-swagger-handler)}]])

(defmethod ig/init-key ::handler
  [_ {:keys [db gpt-queue issue-db repo userbase]}]
  (reitit.ring/ring-handler
   (reitit.ring/router
    [""
     (let [auth-backend (auth-backend userbase)]
       {:muuntaja   m/instance
        :coercion   reitit.coercion.malli/coercion
        :middleware [#(buddy.auth.middleware/wrap-authentication % auth-backend)
                     #(buddy.auth.middleware/wrap-authorization % auth-backend)
                     #(buddy.auth.accessrules/wrap-access-rules % access-rules)
                     defaults-middleware
                     reitit.ring.middleware.muuntaja/format-middleware
                     exception-middleware
                     reitit.ring.coercion/coerce-exceptions-middleware
                     reitit.ring.coercion/coerce-request-middleware
                     reitit.ring.coercion/coerce-response-middleware
                     auth-context-middleware
                     lock/context-middleware]})
     ["/client" server.socket/handle-client]
     ["/git" (git/handlers db repo)]
     ["/index" index/handlers]
     ["/lock" (lock/handlers db)]
     ["/oxygen" oxygen/handlers]
     ["/schedule" (schedule/handlers db issue-db repo)]
     ["/socket" (server.socket/handlers gpt-queue)]
     html-handlers
     swagger-handlers])
   (reitit.ring/routes
    (reitit.ring/redirect-trailing-slash-handler)
    (reitit.ring/create-resource-handler {:path "/assets"})
    (reitit.ring/create-default-handler))))

(defmethod ig/init-key ::server
  [_ {:keys [handler] :as opts}]
  (http-kit/run-server handler (dissoc opts :handler)))

(defmethod ig/halt-key! ::server
  [_ server]
  (server))
