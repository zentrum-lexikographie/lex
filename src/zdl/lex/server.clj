(ns zdl.lex.server
  (:require
   [babashka.fs :as fs]
   [buddy.auth.accessrules]
   [buddy.auth.backends]
   [buddy.auth.middleware]
   [clojure.core.async :as a]
   [clojure.java.io :as io]
   [clojure.data.csv :as csv]
   [integrant.core :as ig]
   [medley.core :refer [dissoc-in]]
   [muuntaja.core :as m]
   [org.httpkit.server :as http-kit]
   [reitit.coercion.malli]
   [reitit.ring]
   [reitit.ring.coercion]
   [reitit.ring.middleware.exception :as ri.ex]
   [reitit.ring.middleware.muuntaja]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.middleware.defaults]
   [ring.util.io :as ring.io]
   [ring.util.response :as resp]
   [ring.websocket :as ws]
   [taoensso.telemere :as tel]
   [zdl.lex.db :as db]
   [zdl.lex.env :as env :refer [getenv]]
   [zdl.lex.git :as git]
   [zdl.lex.html :as html]
   [zdl.lex.index :as index]
   [zdl.lex.issue :as issue]
   [zdl.lex.lock :as lock :refer [with-lock]]
   [zdl.lex.metrics :as metrics]
   [zdl.lex.oxygen :as oxygen]
   [zdl.lex.qa :as qa]
   [zdl.lex.queue :as queue]
   [zdl.lex.schedule :as schedule]
   [zdl.lex.util :refer [pr-edn-str]]
   [zdl.lex.article :as article]))

(def userbase
  (let [userbase-file (fs/file (getenv "USERBASE_FILE" ".htauth.csv"))]
    (or
     (when (fs/readable? userbase-file)
       (with-open [r (io/reader userbase-file)]
         (let [[_header & users] (csv/read-csv r)]
           (into {}
                 (map (fn [[user password desc]]
                        [[user password] {:user        user
                                          :password    password
                                          :description desc}]))
                 users))))
     (do (tel/event! ::fallback-userbase)
         {["admin" "admin"] {:user        "admin"
                             :password    "admin"
                             :description "Administrator"}}))))

(def auth-backend
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

(def new-article-collection
  "Neuartikel/Neuartikel-007")

(defn handle-article-create
  [{{:keys [user]} :identity {{:keys [form pos]} :query} :parameters}]
  (let [xml-id   (index/generate-id)
        filename (article/form->filename form)
        resource (str new-article-collection "/" filename "-" xml-id ".xml")
        xml      (article/new-article-xml xml-id form pos user)]
    (-> (git/write-article-file resource #(spit % xml :encoding "UTF-8"))
        (resp/response)
        (resp/header "X-Lex-Id" resource))))

(defn handle-article-read
  [{{{:keys [resource]} :path} :parameters}]
  (if-let [f (git/get-article-file resource)]
    (resp/response f)
    (resp/not-found resource)))

(defn handle-article-write
  [{:keys [body] {{:keys [resource]} :path} :parameters}]
  (try
    (with-lock
      (fn []
        (if-not (git/get-article-file resource)
          (resp/not-found resource)
          (-> (git/write-article-file resource #(io/copy body %))
              (resp/response)))))
    (catch Throwable t
      (if (lock/locked? t)
        (-> t ex-data :lock (resp/response) (resp/status 423))
        (throw t)))))


(def sockets
  (atom {}))

(def socket-clients
  (atom {}))

(defn socket-message->socket-key
  [{:keys [user client-id]}]
  [user client-id])

(defn socket-reply
  [req response]
  (tel/with-ctx+ {::socket-request req ::socket-response response}
    (if-let [ch (get-in @sockets (socket-message->socket-key req))]
      (try
        (ws/send ch (pr-edn-str response))
        (tel/event! ::socket-response-sent :debug)
        (catch Throwable t
          (tel/error! ::socket-response-failure t)))
      (tel/event! ::socket-response-discarded))))

(defn handle-gpt-message
  [{req :content :as message}]
  (a/go
    (let [resp (a/<! (queue/rpc "gpt" req))
          resp (or (some-> resp :message) {:error :timeout})]
      (a/<!
       (a/io-thread
        (socket-reply message {:content-type :gpt :content resp}))))))

(defn handle-socket
  [{:keys [websocket?] :as req}]
  (if websocket?
    (let [user        (get-in req [:identity :user])
          client-id   (get-in req [:headers "x-lex-client-id"])
          fingerprint [user client-id]]
      {::ws/listener
       {:on-open    (fn [ch]
                      (swap! socket-clients assoc ch fingerprint)
                      (swap! sockets assoc-in fingerprint ch))
        :on-close   (fn [ch _status _reason]
                      (swap! socket-clients dissoc ch)
                      (swap! sockets dissoc-in fingerprint))
        :on-message (fn [_ch message]
                      (let [message (assoc (read-string message) :user user)]
                        (tel/with-ctx+ {::socket-message message}
                          (condp = (message :content-type)
                            :ping (tel/event! ::socket-ping :debug)
                            :gpt  (handle-gpt-message message)
                            (tel/event! ::socket-message)))))}})
    (resp/status 400)))

(def http-port
  (parse-long (getenv "HTTP_PORT" "3000")))

(def middleware
  [#(buddy.auth.middleware/wrap-authentication % auth-backend)
   #(buddy.auth.middleware/wrap-authorization % auth-backend)
   #(buddy.auth.accessrules/wrap-access-rules % access-rules)
   {:name ::defaults
    :wrap #(ring.middleware.defaults/wrap-defaults
            %
            (-> ring.middleware.defaults/site-defaults
                (assoc-in [:proxy] true)
                (assoc-in [:security :anti-forgery] false)))}
   reitit.ring.middleware.muuntaja/format-middleware
   (ri.ex/create-exception-middleware
    (assoc
     ri.ex/default-handlers
     ::ri.ex/wrap
     (fn [handler ^Throwable e request]
       (when-not (some-> e ex-data :type #{:reitit.ring/response}) (tel/error! e))
       (handler e request))))
   reitit.ring.coercion/coerce-exceptions-middleware
   reitit.ring.coercion/coerce-request-middleware
   reitit.ring.coercion/coerce-response-middleware
   {:name ::auth-context
    :wrap (fn [handler]
            (fn [{{:keys [user]} :identity :as req}]
              (tel/with-ctx+ {::user user}
                (cond-> (handler req) user (resp/header "X-Lex-User" user)))))}
   {:name ::lock
    :wrap (fn [handler]
            (fn [{{owner :user}                :identity
                  {{:keys [resource]} :path}   :parameters
                  {{:keys [token ttl]} :query} :parameters
                  :as                          req}]
              (binding [lock/*context* (cond-> {:owner    owner
                                                :resource resource
                                                :token    token}
                                         ttl (assoc :ttl (* ttl 1000)))]
                (tel/with-ctx+ {::lock lock/*context*}
                  (handler req)))))}])

(defmethod ig/init-key ::server
  [_ _]
  (http-kit/run-server
   (reitit.ring/ring-handler
    (reitit.ring/router
     [""
      ["/"
       (constantly
        (-> (html/install "/")
            (resp/response)
            (resp/content-type "text/html")))]
      ["/docs/api/*"
       {:no-doc  true
        :handler (swagger-ui/create-swagger-ui-handler)}]
      ["/git/"
       {:put {:handler    handle-article-create
              :parameters {:query [:map
                                   [:form :string]
                                   [:pos :string]]}}}]
      ["/git/*resource"
       {:get  {:handler    handle-article-read
               :parameters {:path [:map [:resource :string]]}}
        :post {:handler    handle-article-write
               :parameters {:path  [:map [:resource :string]]
                            :query [:map [:token :string]]}}}]
      ["/index"
       {:get {:summary    "Query the full-text index"
              :tags       ["Index" "Query"]
              :parameters {:query [:map
                                   [:q {:optional true} :string]
                                   [:offset {:optional true} [:int {:min 0}]]
                                   [:limit {:optional true} [:int {:min 0}]]]}
              :handler    index/handle-article-query}}]
      ["/index/export"
       {:summary    "Export index metadata in CSV format"
        :tags       ["Index" "Query" "Export"]
        :parameters {:query [:map
                             [:q {:optional true} :string]
                             [:limit {:optional true} :int]]}
        :handler    index/handle-export}]
      ["/index/issues"
       {:get
        {:summary    "Retrieve Mantis issues for a given set of surface forms"
         :tags       ["Mantis" "Issue"]
         :parameters {:query [:map [:q [:or :string [:sequential :string]]]]}
         :handler    index/handle-issue-query}}]
      ["/index/links"
       {:summary    "Retrieve articles based on anchors and links"
        :tags       ["Index" "Query" "Links"]
        :parameters {:query [:map
                             [:anchors
                              {:optional true}
                              [:or :string [:sequential :string]]]
                             [:links
                              {:optional true}
                              [:or :string [:sequential :string]]]]}
        :handler    index/handle-links-query}]
      ["/index/suggest"
       {:get {:summary    "Suggest articles by form"
              :tags       ["Index" "Query" "Auto-Complete"]
              :parameters {:query [:map [:q :string]]}
              :handler    index/handle-article-suggest}}]
      ["/lock"
       {:summary "Retrieve list of active locks"
        :tags    ["Lock" "Query"]
        :handler lock/handle-read-locks}]
      ["/lock/*resource"
       {:get    {:summary    "Read a resource lock"
                 :tags       ["Lock" "Query" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map [:token :string]]}
                 :handler    lock/handle-read-lock}
        :post   {:summary    "Set a resource lock"
                 :tags       ["Lock" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map
                                      [:token :string]
                                      [:ttl [:int {:min 1}]]]}
                 :handler    lock/handle-create-lock}
        :delete {:summary    "Remove a resource lock."
                 :tags       ["Lock" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map [:token :string]]}
                 :handler    lock/handle-remove-lock}}]
      ["/oxygen/updateSite.xml"
       (constantly
        (->  (resp/response oxygen/update-descriptor)
             (resp/content-type "application/xml")))]
      ["/oxygen/zdl-lex-framework.zip"
       (fn [_]
         (resp/response (ring.io/piped-input-stream oxygen/download-framework)))]
      ["/oxygen/zdl-lex-plugin.zip"
       (fn [_]
         (resp/response (ring.io/piped-input-stream oxygen/download-plugin)))]
      ["/schedule/commit"
       {:patch {:summary "Commit pending changes on the server's branch"
                :tags    ["Article" "Git" "Admin"]
                :handler (schedule/trigger-task git/commit!)}}]
      ["/schedule/git/:ref"
       {:post  {:summary    "Fast-forwards the server's branch to the given ref"
                :tags       ["Article" "Git" "Admin"]
                :parameters {:path [:map [:ref :string]]}
                :handler    git/handle-fast-forward}
        :patch {:summary    "Rebases the server's branch to the given ref"
                :tags       ["Article" "Git" "Admin"]
                :parameters {:path [:map [:ref :string]]}
                :handler    git/handle-rebase}}]
      ["/schedule/qa"
       {:patch {:summary "Edits article data"
                :tags    ["Article", "Git", "Admin"]
                :handler (schedule/trigger-task qa/edit-articles!)}}]
      ["/schedule/index"
       {:patch {:summary "Refreshes all article data in index"
                :tags    ["Index", "Admin"]
                :handler (schedule/trigger-task index/sync-articles!)}}]
      ["/schedule/issues"
       {:patch {:summary "Clears the Mantis issue index and re-synchronizes it"
                :tags    ["Mantis" "Admin"]
                :handler (schedule/trigger-task issue/sync!)}}]
      ["/socket"
       handle-socket]
      ["/styles.css"
       (constantly
        (-> html/css
            (resp/response)
            (resp/content-type "text/css")))]
      ["/swagger.json"
       {:no-doc  true
        :handler (swagger/create-swagger-handler)}]]
     {:data {:muuntaja   m/instance
             :coercion   reitit.coercion.malli/coercion
             :middleware middleware}})
    (reitit.ring/routes
     (reitit.ring/redirect-trailing-slash-handler)
     (reitit.ring/create-resource-handler {:path "/assets"})
     (reitit.ring/create-default-handler)))
   {:port http-port}))

(defmethod ig/halt-key! ::server
  [_ server]
  (server))

(def config
  {::db/pool          {}
   ::queue/rpc-client {}
   ::index/git-sync   {}
   ::server           {}})

(def schedule-config
  {::metrics/reporter {}
   ::schedule/tasks   {}})

(defn -main
  [& _]
  (let [system (ig/init (merge config schedule-config))]
    (. (Runtime/getRuntime)
       (addShutdownHook (Thread. #(ig/halt! system))))
    (future
      (tel/catch->error! ::init (when (git/init!) (index/sync-articles!)))))
  @(promise))
