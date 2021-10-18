(ns zdl.lex.server.http
  (:require [aleph.http :as aleph-http]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [hiccup.page :refer [include-css]]
            [mount.core :as mount :refer [defstate]]
            [reitit.coercion.malli :as rcm]
            [reitit.http :as http]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.parameters :as parameters]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring :as ring]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.util.io :as rio]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.auth :as auth]
            [zdl.lex.server.format :as format]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.graph.article :as graph-article]
            [zdl.lex.server.lock :as lock]
            [zdl.lex.server.oxygen :as oxygen]
            [zdl.lex.server.index :as index]
            [zdl.lex.server.tasks :as tasks]))

(def homepage
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    (include-css "/assets/bulma-0.7.4.min.css")
    [:title "ZDL Lex-Server"]]
   [:body
    [:div.hero.is-primary.is-bold.is-fullheight
     [:div.hero-body
      [:div.container
       [:h1.title "ZDL Lex-Server"]
       [:h2.subtitle
        [:a
         {:href  "/oxygen/updateSite.xml"
          :title "Oxygen XML Editor - Update Site"}
         "Oxygen XML Editor - Update Site"]]]]]]])

(defn wrap-log-exception [handler ^Throwable e req]
  (log/warn e (select-keys req [:uri :request-method]))
  (handler e req))

(def exception-handlers
  (assoc exception/default-handlers ::exception/wrap wrap-log-exception))

(defn pipe-resource
  [resource]
  (rio/piped-input-stream
   (fn [os]
     (with-open [is (io/input-stream resource)]
       (io/copy is os)))))

(require 'sieppari.async.core-async)

(def handler
  (http/ring-handler
   (http/router
    [""
     ["/"
      (constantly
       {:status  307
        :headers {"Location" "/home"}})]
     ["/article" {::auth/roles #{:user}}
      ["/"
       {:put    {:handler    article/create-article
                 :parameters {:query [:map
                                      [:form :string]
                                      [:pos :string]]}}
        :delete {:summary     "Refreshes all article data"
                 :tags        ["Index", "Admin"]
                 :handler     tasks/trigger-articles-refresh
                 ::auth/roles #{:admin}}}]
      ["/*resource"
       {:get  {:handler    article/get-article
               :parameters {:path [:map [:resource :string]]}}
        :post {:handler    (lock/wrap-resource-lock article/post-article)
               :parameters {:path  [:map [:resource :string]]
                            :query [:map [:token :string]]}}}]]
     ["/docs/api/*"
      {:no-doc  true
       :handler (swagger-ui/create-swagger-ui-handler)}]
     ["/git"
      {:patch {:summary     "Commit pending changes on the server's branch"
               :tags        ["Article" "Git" "Admin"]
               :handler     tasks/trigger-git-commit
               ::auth/roles #{:admin}}}]
     ["/git/ff/:ref"
      {:post {:summary     "Fast-forwards the server's branch to the given refs"
              :tags        ["Article" "Git" "Admin"]
              :parameters  {:path [:map [:ref :string]]}
              :handler     (fn [req]
                             (let [ref (get-in req [:parameters :path :ref])]
                               (try
                                 {:status 200 :body (git/fast-forward! ref)}
                                 (catch Throwable t
                                   (log/warn t)
                                   {:status 400 :body ref}))))
              ::auth/roles #{:admin}}}]
     ["/graph/*resource"
      {:handler graph-article/get-article-graph
       :parameters {:path [:map [:resource :string]]}}]
     ["/home"
      (constantly
       {:status                200
        :muuntaja/encode       true
        :muuntaja/content-type "text/html"
        :body                  homepage})]
     ["/index" {::auth/roles #{:user}}
      [""
       {:summary    "Query the full-text index"
        :tags       ["Index" "Query"]
        :parameters {:query [:map
                             [:q {:optional true} :string]
                             [:offset {:optional true} [:int {:min 0}]]
                             [:limit {:optional true} [:int {:min 0}]]]}
        :handler index/search-articles}]
      ["/export"
       {:summary    "Export index metadata in CSV format"
        :tags       ["Index" "Query" "Export"]
        :parameters {:query [:map
                             [:q {:optional true} :string]
                             [:limit {:optional true} :int]]}
        :handler    index/export-article-metadata}]
      ["/forms/suggestions"
       {:summary    "Retrieve suggestion for headwords based on prefix queries"
        :tags       ["Index" "Query" "Suggestions" "Headwords"]
        :parameters {:query [:map [:q {:optional true} :string]]}
        :handler    index/suggest-forms}]]
     ["/lock" {::auth/roles #{:user}}
      [""
       {:summary "Retrieve list of active locks"
        :tags    ["Lock" "Query"]
        :handler lock/read-locks}]
      ["/*resource"
       {:get    {:summary    "Read a resource lock"
                 :tags       ["Lock" "Query" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map [:token :string]]}
                 :handler    lock/read-lock}
        :post   {:summary    "Set a resource lock"
                 :tags       ["Lock" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map
                                      [:token :string]
                                      [:ttl [:int {:min 1}]]]}
                 :handler    lock/create-lock}
        :delete {:summary    "Remove a resource lock."
                 :tags       ["Lock" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map [:token :string]]}
                 :handler    lock/remove-lock}}]]
     ["/mantis"
      {:delete
       {:summary     "Clears the internal Mantis issue index and re-synchronizes it"
        :tags        ["Mantis" "Admin"]
        :handler     tasks/trigger-mantis-sync
        ::auth/roles #{:admin}}}]
     ["/oxygen"
      ["/updateSite.xml"
       (constantly
        {:status                200
         :body                  oxygen/update-descriptor
         :muuntaja/encode       true
         :muuntaja/content-type "application/xml"})]
      ["/zdl-lex-framework.zip"
       (fn [_]
         {:status 200
          :body   (rio/piped-input-stream oxygen/download-framework)})]
      ["/zdl-lex-plugin.zip"
       (fn [_]
         {:status 200
          :body   (rio/piped-input-stream oxygen/download-plugin)})]]
     ["/status"
      {:summary     "Provides status information, e.g. logged-in user"
       :tags        ["Status"]
       :handler     auth/get-status
       ::auth/roles #{:user}}]
     ["/swagger.json"
      {:no-doc  true
       :handler (swagger/create-swagger-handler)}]]
    {;;:reitit.interceptor/transform dev/print-context-diffs
     ;;:exception pretty/exception
     :data {:coercion rcm/coercion
            :muuntaja format/customized-muuntaja}})
   (ring/routes
    (ring/create-resource-handler {:path "/assets"})
    (ring/create-default-handler))
   {:executor     sieppari/executor
    :interceptors [swagger/swagger-feature
                   (parameters/parameters-interceptor)
                   (muuntaja/format-negotiate-interceptor)
                   (muuntaja/format-response-interceptor)
                   (exception/exception-interceptor exception-handlers)
                   (muuntaja/format-request-interceptor)
                   (coercion/coerce-response-interceptor)
                   (coercion/coerce-request-interceptor)
                   auth/interceptor]}))

(defstate server
  :start (aleph-http/start-server
          handler
          {:port (Integer/parseInt (getenv "HTTP_PORT" "3000"))})
  :stop (.close ^java.io.Closeable server))

(comment
  (mount/start #'server))
