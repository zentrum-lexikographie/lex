(ns zdl.lex.server.http
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
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
            [ring.adapter.jetty :as jetty]
            [ring.util.io :as ring.io]
            [zdl.lex.cron :as cron]
            [zdl.lex.server.article.handler :as article.handler]
            [zdl.lex.server.auth :as auth]
            [zdl.lex.server.git :as server.git]
            [zdl.lex.server.gpt :as server.gpt]
            [zdl.lex.server.html :as html]
            [zdl.lex.server.http.format]
            [zdl.lex.server.issue :as server.issue]
            [zdl.lex.server.article.lock :as server.article.lock]
            [zdl.lex.server.oxygen :as oxygen]
            [zdl.lex.server.solr.export :as solr.export]
            [zdl.lex.server.solr.query :as solr.query]
            [zdl.lex.server.solr.links :as solr.links]
            [zdl.lex.server.solr.suggest :as solr.suggest]
            [integrant.core :as ig])
  (:import org.eclipse.jetty.server.Server))

(defn wrap-log-exception
  [handler ^Throwable e req]
  (log/warn e (select-keys req [:uri :request-method]))
  (handler e req))

(def exception-handlers
  (assoc exception/default-handlers ::exception/wrap wrap-log-exception))

(defn pipe-resource
  [resource]
  (ring.io/piped-input-stream
   (fn [os]
     (with-open [is (io/input-stream resource)]
       (io/copy is os)))))

(require 'sieppari.async.core-async)

(defn client-resources
  [context-path]
  [context-path
   ["/updateSite.xml"
    (constantly
     {:status                200
      :body                  oxygen/update-descriptor
      :muuntaja/encode       true
      :muuntaja/content-type "application/xml"})]
   ["/zdl-lex-framework.zip"
    (fn [_]
      {:status 200
       :body   (ring.io/piped-input-stream oxygen/download-framework)})]
   ["/zdl-lex-plugin.zip"
    (fn [_]
      {:status 200
       :body   (ring.io/piped-input-stream oxygen/download-plugin)})]])

(defn trigger-cron-handler
  [schedule]
  (fn [_]
    {:status 200
     :body   {:triggered (cron/trigger! schedule)}}))

(defn handler
  [{:keys [schedule lock-db git-repo] {git-dir :dir} :git-repo}]
  (http/ring-handler
   (http/router
    [""
     ["/"
      (constantly
       {:status  307
        :headers {"Location" "/install"}})]
     ["/article" {::auth/roles #{:user}}
      ["/"
       {:put {:handler    (partial article.handler/handle-create git-dir)
              :parameters {:query [:map
                                   [:form :string]
                                   [:pos :string]]}}
        :patch {:summary     "Edits article data"
                :tags        ["Article", "Git", "Admin"]
                :handler     (trigger-cron-handler (:article-edit schedule))
                ::auth/roles #{:admin}}}]
      ["/*resource"
       {:get  {:handler    (partial article.handler/handle-read git-dir)
               :parameters {:path [:map [:resource :string]]}}
        :post {:handler    (partial article.handler/handle-write git-dir lock-db)
               :parameters {:path  [:map [:resource :string]]
                            :query [:map [:token :string]]}}}]]
     ["/docs/api/*"
      {:no-doc  true
       :handler (swagger-ui/create-swagger-ui-handler)}]
     ["/git"
      {:patch {:summary     "Commit pending changes on the server's branch"
               :tags        ["Article" "Git" "Admin"]
               :handler     (trigger-cron-handler (:git-commit schedule))
               ::auth/roles #{:admin}}}]
     ["/gpt" {::auth/roles #{:user}}
      ["/definitions"
       {:get {:summary    "Query GPT model for definitions"
              :tags       ["GPT" "Query" "Definitions"]
              :parameters {:query [:map [:q :string]]}
              :handler    server.gpt/handle-definitions}}]]
     ["/git/ff/:ref"
      {:post  {:summary     "Fast-forwards the server's branch to the given ref"
               :tags        ["Article" "Git" "Admin"]
               :parameters  {:path [:map [:ref :string]]}
               :handler     (partial server.git/handle-fast-forward git-repo)
               ::auth/roles #{:admin}}
       :patch {:summary     "Rebases the server's branch to the given ref"
               :tags        ["Article" "Git" "Admin"]
               :parameters  {:path [:map [:ref :string]]}
               :handler     (partial server.git/handle-rebase git-repo)
               ::auth/roles #{:admin}}}]
     ["/install"
      (constantly
       {:status                200
        :muuntaja/encode       false
        :body                  (html/install "/")})]
     ["/index" {::auth/roles #{:user}}
      [""
       {:get   {:summary    "Query the full-text index"
                :tags       ["Index" "Query"]
                :parameters {:query [:map
                                     [:q {:optional true} :string]
                                     [:offset {:optional true} [:int {:min 0}]]
                                     [:limit {:optional true} [:int {:min 0}]]]}
                :handler    solr.query/handle-query}
        :patch {:summary     "Refreshes all article data in index"
                :tags        ["Index", "Admin"]
                :handler     (trigger-cron-handler (:git-refresh schedule))
                ::auth/roles #{:admin}}}]
      ["/export"
       {:summary    "Export index metadata in CSV format"
        :tags       ["Index" "Query" "Export"]
        :parameters {:query [:map
                             [:q {:optional true} :string]
                             [:limit {:optional true} :int]]}
        :handler    solr.export/handle-export}]
      ["/forms/suggestions"
       {:summary    "Retrieve suggestion for headwords based on prefix queries"
        :tags       ["Index" "Query" "Suggestions" "Headwords"]
        :parameters {:query [:map [:q {:optional true} :string]]}
        :handler    solr.suggest/suggest-forms}]
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
        :handler    solr.links/handle-query}]]
     ["/lock" {::auth/roles #{:user}}
      [""
       {:summary "Retrieve list of active locks"
        :tags    ["Lock" "Query"]
        :handler (partial server.article.lock/read-locks lock-db)}]
      ["/*resource"
       {:get    {:summary    "Read a resource lock"
                 :tags       ["Lock" "Query" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map [:token :string]]}
                 :handler    (partial server.article.lock/read-lock lock-db)}
        :post   {:summary    "Set a resource lock"
                 :tags       ["Lock" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map
                                      [:token :string]
                                      [:ttl [:int {:min 1}]]]}
                 :handler    (partial server.article.lock/create-lock lock-db)}
        :delete {:summary    "Remove a resource lock."
                 :tags       ["Lock" "Resource"]
                 :parameters {:path  [:map [:resource :string]]
                              :query [:map [:token :string]]}
                 :handler    (partial server.article.lock/remove-lock lock-db)}}]]
     ["/mantis"
      {:get
       {:summary    "Retrieve Mantis issues for a given set of surface forms"
        :tags       ["Mantis" "Issue"]
        :parameters {:query [:map [:q [:or :string [:sequential :string]]]]}
        :handler    server.issue/handle-query}
       :delete
       {:summary     "Clears the internal Mantis issue index and re-synchronizes it"
        :tags        ["Mantis" "Admin"]
        :handler     (trigger-cron-handler (:issue-sync schedule))
        ::auth/roles #{:admin}}}]
     (client-resources "/oxygen")
     (client-resources "/zdl-lex-client")
     ["/status"
      {:summary     "Provides status information, e.g. logged-in user"
       :tags        ["Status"]
       :handler     auth/get-status
       ::auth/roles #{:user}}]
     ["/swagger.json"
      {:no-doc  true
       :handler (swagger/create-swagger-handler)}]
     ["/styles.css"
      (constantly
       {:status          200
        :muuntaja/encode false
        :body            html/css})]]
    {;;:reitit.interceptor/transform dev/print-context-diffs
     ;;:exception pretty/exception
     :data {:coercion rcm/coercion
            :muuntaja zdl.lex.server.http.format/customized-muuntaja}})
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

(defmethod ig/init-key ::server
  [_ {:keys [port] :as config}]
  (log/infof "Starting HTTP server @ %d/tcp" port)
  (jetty/run-jetty (handler config) {:port port :join? false}))

(defmethod ig/halt-key! ::server
  [_ ^Server server]
  (.stop ^Server server)
  (.join ^Server server))
