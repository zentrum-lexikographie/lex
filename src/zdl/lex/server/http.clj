(ns zdl.lex.server.http
  (:require
   [clojure.set :as sets]
   [clojure.string :as str]
   [muuntaja.core :as m]
   [reitit.coercion.malli]
   [reitit.core]
   [reitit.ring]
   [reitit.ring.coercion]
   [reitit.ring.middleware.exception]
   [reitit.ring.middleware.muuntaja]
   [reitit.ring.middleware.parameters]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]
   [ring.util.io :as ring.io]
   [ring.util.response :as resp]
   [taoensso.telemere :as t]
   [zdl.lex.env :as env]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.html :as html]
   [zdl.lex.server.index :as index]
   [zdl.lex.server.issue :as issue]
   [zdl.lex.server.lock :as lock]
   [zdl.lex.server.oxygen :as oxygen])
  (:import
   (java.util Base64)))

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

(def decode-base64
  (let [decoder (Base64/getDecoder)]
    #(str/join (map char (.decode decoder (.getBytes ^String %))))))

(defn authenticate
  [{{auth "authorization"} :headers :as req}]
  (let [[user password] (some-> auth
                                (str/replace #"^Basic " "")
                                (decode-base64)
                                (str/split #":" 2))
        authenticated?  (and user password
                             (or (and (empty? env/server-user)
                                      (empty? env/server-password))
                                 (and (= env/server-user user)
                                      (= env/server-password password))))]
    (cond-> req authenticated? (assoc ::user user ::password password))))

(def auth-middleware
  {:name ::auth
   :wrap (fn [handler]
           (fn [{method :request-method :reitit.core/keys [match] :as req}]
             (let [required-roles (or (get-in match [:data method ::roles])
                                      (get-in match [:data ::roles]))]
               (if (empty? required-roles)
                 (handler req)
                 (let [req   (authenticate req)
                       user  (::user req)
                       roles (cond-> #{}
                               user             (conj :user)
                               (= "admin" user) (conj :admin))]
                   (if (sets/subset? required-roles roles)
                     (handler (assoc req ::roles roles))
                     (-> (resp/response "Access denied")
                         (resp/status 401)
                         (resp/header "WWW-Authenticate"
                                      "Basic realm=\"ZDL-Lex-Server\"")
                         (resp/header "Content-Type" "text/plain"))))))))})

(defn log-exceptions
  [handler ^Throwable e request]
  (when-not (some-> e ex-data :type #{:reitit.ring/response}) (t/error! e))
  (handler e request))

(def exception-middleware
  (-> reitit.ring.middleware.exception/default-handlers
      (assoc :reitit.ring.middleware.exception/wrap log-exceptions)
      (reitit.ring.middleware.exception/create-exception-middleware)))

(def handler-options
  {:muuntaja   m/instance
   :coercion   reitit.coercion.malli/coercion
   :middleware [proxy-headers-middleware
                reitit.ring.middleware.parameters/parameters-middleware
                reitit.ring.middleware.muuntaja/format-middleware
                exception-middleware
                reitit.ring.coercion/coerce-exceptions-middleware
                reitit.ring.coercion/coerce-request-middleware
                reitit.ring.coercion/coerce-response-middleware
                auth-middleware
                lock/context-middleware]})

(defn client-resources
  [context-path]
  [context-path
   ["/updateSite.xml"
    (constantly
     (-> oxygen/update-descriptor
         (resp/response)
         (resp/content-type "application/xml")))]
   ["/zdl-lex-framework.zip"
    (fn [_]
      (resp/response (ring.io/piped-input-stream oxygen/download-framework)))]
   ["/zdl-lex-plugin.zip"
    (fn [_]
      (resp/response (ring.io/piped-input-stream oxygen/download-plugin)))]])

(defn trigger-task
  [task]
  (fn [_]
    (future (try (task) (catch Throwable t (t/error! t))))
    (resp/response {:triggered true})))

(def handler
  (reitit.ring/ring-handler
   (reitit.ring/router
    ["" handler-options
     ["/"
      (constantly (resp/redirect "/install" 307))]
     ["/article" {::roles #{:user}}
      ["/"
       {:put {:handler    git/handle-create
              :parameters {:query [:map
                                   [:form :string]
                                   [:pos :string]]}}
        :patch {:summary     "Edits article data"
                :tags        ["Article", "Git", "Admin"]
                :handler     (trigger-task git/qa!)
                ::roles #{:admin}}}]
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
               :handler     (trigger-task git/commit!)
               ::roles #{:admin}}}]
     ["/git/ff/:ref"
      {:post  {:summary     "Fast-forwards the server's branch to the given ref"
               :tags        ["Article" "Git" "Admin"]
               :parameters  {:path [:map [:ref :string]]}
               :handler     git/handle-fast-forward
               ::roles #{:admin}}
       :patch {:summary     "Rebases the server's branch to the given ref"
               :tags        ["Article" "Git" "Admin"]
               :parameters  {:path [:map [:ref :string]]}
               :handler     git/handle-rebase
               ::roles #{:admin}}}]
     ["/install"
      (constantly (-> (html/install "/")
                      (resp/response)
                      (resp/content-type "text/html")))]
     ["/index" {::roles #{:user}}
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
        :handler    index/handle-links-query}]]
     ["/lock" {::roles #{:user}}
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
        :handler     (trigger-task issue/sync!)
        ::roles #{:admin}}}]
     (client-resources "/oxygen")
     (client-resources "/zdl-lex-client")
     ["/status"
      {:summary "Provides status information, e.g. logged-in user"
       :tags    ["Status"]
       :handler (fn [{::keys [user password roles]}]
                  (resp/response {:user     user
                                  :password password
                                  :roles    roles}))
       ::roles  #{:user}}]
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
