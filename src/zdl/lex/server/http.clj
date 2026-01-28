(ns zdl.lex.server.http
  (:require
   [buddy.auth.accessrules]
   [buddy.auth.backends]
   [buddy.auth.middleware]
   [clojure.core.async :as a]
   [integrant.core :as ig]
   [medley.core :refer [dissoc-in]]
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
   [ring.websocket :as ws]
   [taoensso.telemere :as tm]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.gpt :as gpt]
   [zdl.lex.server.html :as html]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.lock :as lock]
   [zdl.lex.server.oxygen :as oxygen]
   [zdl.lex.server.schedule :as schedule]
   [zdl.lex.util :refer [pr-edn-str]]))

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


(defn auth-backend
  [userbase]
  (buddy.auth.backends/basic
   {:realm                "ZDL-Lex-Server"
    :authfn               (fn [_request {:keys [username password] :as _auth}]
                            (get userbase [username password]))
    :unauthorized-handler (fn [request {:keys [realm] :as _auth-data}]
                            (if (:identity request)
                              (-> (resp/response "Permission denied")
                                  (resp/status 403))
                              (-> (resp/response "Unauthorized")
                                  (resp/header "WWW-Authenticate"
                                               (format "Basic realm=\"%s\"" realm))
                                  (resp/status 401))))}))

(def access-rules
  (letfn [(authenticated? [{id :identity :as _req}] (some? id))
          (admin? [{{:keys [user]} :identity :as _req}] (= "admin" user))
          (public? [_req] true)]
    {:rules [{:pattern #"^/git.*" :handler authenticated?}
             {:pattern #"^/index.*" :handler authenticated?}
             {:pattern #"^/lock.*" :handler authenticated?}
             {:pattern #"^/schedule.*" :handler admin?}
             {:pattern #"^/socket.*" :handler authenticated?}
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

(def sockets
  (atom {}))

(def socket-clients
  (atom {}))

(defn socket-message->socket-key
  [{:keys [user client-id]}]
  [user client-id])

(defn socket-reply
  [req response]
  (let [data {:request req :response response}]
    (if-let [ch (get-in @sockets (socket-message->socket-key req))]
      (try
        (ws/send ch (pr-edn-str response))
        (tm/log! {:id ::socket-reply-success :level :info :data data})
        (catch Throwable t
          (tm/error! {:id ::socket-reply-failure :data data} t)))
      (tm/log! {:id ::socket-reply-discarded :level :info :data data}))))


(defmulti handle-socket-request
  (fn [_gpt_queue {:keys [content-type]}] content-type))

(defmethod handle-socket-request :gpt
  [gpt-queue {gpt-req :content :as req}]
  (a/go
    (let [gpt-exchange (gpt/->exchange gpt-req)
          gpt-resp     (a/<! (gpt/async-complete gpt-queue gpt-exchange))
          gpt-resp     (or (some-> gpt-resp :message) {:error :timeout})]
      (a/<!
       (a/io-thread
        (socket-reply req {:content-type :gpt :content gpt-resp}))))))

(defmethod handle-socket-request :ping
  [_gpt-queue req]
  (tm/log! {:id ::socket-ping :level :debug :data {:request req}}))

(defmethod handle-socket-request :default
  [_gpt-queue req]
  (tm/log! {:id ::socket-request :level :info :data {:request req}}))

(defn socket-opened
  [fingerprint ch]
  (swap! socket-clients assoc ch fingerprint)
  (swap! sockets assoc-in fingerprint ch))

(defn socket-message-received
  [gpt-queue user _ch message]
  (let [message (assoc (read-string message) :user user)]
    (handle-socket-request gpt-queue message)))

(defn socket-closing
  [fingerprint ch _status _reason]
  (swap! socket-clients dissoc ch)
  (swap! sockets dissoc-in fingerprint))

(defn handle-socket
  [gpt-queue {:keys [websocket?] :as req}]
  (if websocket?
    (let [user        (get-in req [:identity :user])
          client-id   (get-in req [:headers "x-lex-client-id"])
          fingerprint [user client-id]]
      {::ws/listener {:on-open    (partial socket-opened fingerprint)
                      :on-message (partial socket-message-received gpt-queue user)
                      :on-close   (partial socket-closing fingerprint)}})
    (resp/status 400)))

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
     ["/git" (git/handlers db repo)]
     ["/index" index/handlers]
     ["/lock" (lock/handlers db)]
     ["/oxygen" oxygen/handlers]
     ["/schedule" (schedule/handlers db issue-db repo)]
     ["/socket" (partial handle-socket gpt-queue)]
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
