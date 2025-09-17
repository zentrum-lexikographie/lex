(ns zdl.lex.server.http
  (:require
   [buddy.auth.accessrules]
   [buddy.auth.backends]
   [buddy.auth.middleware]
   [clojure.string :as str]
   [muuntaja.core :as m]
   [reitit.coercion.malli]
   [reitit.ring]
   [reitit.ring.coercion]
   [reitit.ring.middleware.exception]
   [reitit.ring.middleware.muuntaja]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.defaults]
   [ring.util.io :as ring.io]
   [ring.util.response :as resp]
   [taoensso.telemere :as tm]
   [zdl.lex.env :as env]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.html :as html]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.issue :as issue]
   [zdl.lex.server.lock :as lock]
   [zdl.lex.server.oxygen :as oxygen]))

(def handler-defaults
  (-> ring.middleware.defaults/site-defaults
      (assoc-in [:proxy] true)
      (assoc-in [:security :anti-forgery] false)))

(def defaults-middleware
  {:name ::defaults
   :wrap #(ring.middleware.defaults/wrap-defaults % handler-defaults)})

(defn proxy-headers->request
  [{:keys [headers] :as request}]
  (let [scheme      (some->
                     (or (headers "x-forwarded-proto") (headers "x-scheme"))
                     (str/lower-case) (keyword) #{:http :https})
        remote-addr (some->>
                     (headers "x-forwarded-for") (re-find #"^[^,]*")
                     (str/trim) (not-empty))]
    (cond-> request
      scheme      (assoc :scheme scheme)
      remote-addr (assoc :remote-addr remote-addr))))

(def proxy-headers-middleware
  {:name ::proxy-headers
   :wrap (fn [handler]
           (fn
             ([request]
              (handler (proxy-headers->request request)))
             ([request respond raise]
              (handler (proxy-headers->request request) respond raise))))})

(defn log-exceptions
  [handler ^Throwable e request]
  (when-not (some-> e ex-data :type #{:reitit.ring/response}) (tm/error! e))
  (handler e request))

(def exception-middleware
  (-> reitit.ring.middleware.exception/default-handlers
      (assoc :reitit.ring.middleware.exception/wrap log-exceptions)
      (reitit.ring.middleware.exception/create-exception-middleware)))

(defn authenticate
  [_request {:keys [username password] :as _auth-data}]
  (get env/userbase [username password]))

(defn handle-unauthorized
  [request {:keys [realm] :as _auth-data}]
  (if (:identity request)
    (-> (resp/response "Permission denied")
        (resp/status 403))
    (-> (resp/response "Unauthorized")
        (resp/header "WWW-Authenticate" (format "Basic realm=\"%s\"" realm))
        (resp/status 401))))

(def auth-backend
  (buddy.auth.backends/basic {:realm                "ZDL-Lex-Server"
                              :authfn               authenticate
                              :unauthorized-handler handle-unauthorized}))

(def access-rules
  (letfn [(authenticated? [{id :identity :as _req}] (some? id))
          (admin? [{{:keys [user]} :identity :as _req}] (= "admin" user))
          (public? [_req] true)]
    {:rules [{:pattern #"^/article.*" :handler authenticated?}
             {:pattern #"^/git.*" :handler admin?}
             {:pattern #"^/index.*" :request-method :get :handler authenticated?}
             {:pattern #"^/index.*" :handler admin?}
             {:pattern #"^/lock.*" :handler authenticated?}
             {:pattern #"^/mantis.*" :request-method :get :handler authenticated?}
             {:pattern #"^/mantis.*" :handler admin?}
             {:pattern #"^/status.*" :handler authenticated?}
             {:pattern #"^/.*" :handler public?}]}))

(def handler-options
  {:muuntaja   m/instance
   :coercion   reitit.coercion.malli/coercion
   :middleware [#(buddy.auth.middleware/wrap-authentication % auth-backend)
                #(buddy.auth.middleware/wrap-authorization % auth-backend)
                #(buddy.auth.accessrules/wrap-access-rules % access-rules)
                proxy-headers-middleware
                defaults-middleware
                reitit.ring.middleware.muuntaja/format-middleware
                exception-middleware
                reitit.ring.coercion/coerce-exceptions-middleware
                reitit.ring.coercion/coerce-request-middleware
                reitit.ring.coercion/coerce-response-middleware
                lock/context-middleware]})

(defn trigger-task
  [task]
  (fn [_]
    (future (try (task) (catch Throwable t (tm/error! t))))
    (resp/response {:triggered true})))

(def handler
  (reitit.ring/ring-handler
   (reitit.ring/router
    ["" handler-options
     ["/"
      (constantly (resp/redirect "/install" 307))]
     ["/article"
      ["/"
       {:put {:handler    git/handle-create
              :parameters {:query [:map
                                   [:form :string]
                                   [:pos :string]]}}
        :patch {:summary     "Edits article data"
                :tags        ["Article", "Git", "Admin"]
                :handler     (trigger-task git/qa!)}}]
      ["/*resource"
       {:get  {:handler    git/handle-read
               :parameters {:path [:map [:resource :string]]}}
        :post {:handler    git/handle-write
               :parameters {:path  [:map [:resource :string]]
                            :query [:map [:token :string]]}}}]]
     ["/docs/api/*"
      {:no-doc  true
       :handler (swagger-ui/create-swagger-ui-handler)}]
     ["/git"
      {:patch {:summary     "Commit pending changes on the server's branch"
               :tags        ["Article" "Git" "Admin"]
               :handler     (trigger-task git/commit!)}}]
     ["/git/ff/:ref"
      {:post  {:summary     "Fast-forwards the server's branch to the given ref"
               :tags        ["Article" "Git" "Admin"]
               :parameters  {:path [:map [:ref :string]]}
               :handler     git/handle-fast-forward}
       :patch {:summary     "Rebases the server's branch to the given ref"
               :tags        ["Article" "Git" "Admin"]
               :parameters  {:path [:map [:ref :string]]}
               :handler     git/handle-rebase}}]
     ["/install"
      (constantly (-> (html/install "/")
                      (resp/response)
                      (resp/content-type "text/html")))]
     ["/index"
      [""
       {:get   {:summary    "Query the full-text index"
                :tags       ["Index" "Query"]
                :parameters {:query [:map
                                     [:q {:optional true} :string]
                                     [:offset {:optional true} [:int {:min 0}]]
                                     [:limit {:optional true} [:int {:min 0}]]]}
                :handler    index/handle-article-query}
        :patch {:summary     "Refreshes all article data in index"
                :tags        ["Index", "Admin"]
                :handler     (trigger-task git/sync-index!)
                ::roles #{:admin}}}]
      ["/export"
       {:summary    "Export index metadata in CSV format"
        :tags       ["Index" "Query" "Export"]
        :parameters {:query [:map
                             [:q {:optional true} :string]
                             [:limit {:optional true} :int]]}
        :handler    index/handle-export}]
      ["/links"
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
      ["/suggest"
       {:get   {:summary    "Suggest articles by form"
                :tags       ["Index" "Query" "Auto-Complete"]
                :parameters {:query [:map [:q :string]]}
                :handler    index/handle-article-suggest}}]]
     ["/lock"
      [""
       {:summary "Retrieve list of active locks"
        :tags    ["Lock" "Query"]
        :handler lock/handle-read-locks}]
      ["/*resource"
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
                 :handler    lock/handle-remove-lock}}]]
     ["/mantis"
      {:get
       {:summary    "Retrieve Mantis issues for a given set of surface forms"
        :tags       ["Mantis" "Issue"]
        :parameters {:query [:map [:q [:or :string [:sequential :string]]]]}
        :handler    index/handle-issue-query}
       :delete
       {:summary     "Clears the internal Mantis issue index and re-synchronizes it"
        :tags        ["Mantis" "Admin"]
        :handler     (trigger-task issue/sync!)}}]
     ["/oxygen"
      ["/updateSite.xml"
       (constantly
        (->  (resp/response oxygen/update-descriptor)
             (resp/content-type "application/xml")))]
      ["/zdl-lex-framework.zip"
       (fn [_]
         (resp/response (ring.io/piped-input-stream oxygen/download-framework)))]
      ["/zdl-lex-plugin.zip"
       (fn [_]
         (resp/response (ring.io/piped-input-stream oxygen/download-plugin)))]]
     ["/status"
      {:summary "Provides status information, e.g. logged-in user"
       :tags    ["Status"]
       :handler (comp resp/response :identity)}]
     ["/swagger.json"
      {:no-doc  true
       :handler (swagger/create-swagger-handler)}]
     ["/styles.css"
      (constantly (-> html/css (resp/response) (resp/content-type "text/css")))]])
   (reitit.ring/routes
    (reitit.ring/redirect-trailing-slash-handler)
    (reitit.ring/create-resource-handler {:path "/assets"})
    (reitit.ring/create-default-handler))))

(def ^:dynamic server
  nil)

(defn stop-server
  []
  (when server
    (.stop server)
    (.join server)
    (alter-var-root #'server (constantly nil))))

(defn start-server
  []
  (stop-server)
  (->>
   (jetty/run-jetty handler {:port env/http-port :join? false})
   (constantly)
   (alter-var-root #'server)))
