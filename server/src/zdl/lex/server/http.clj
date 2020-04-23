(ns zdl.lex.server.http
  (:require [mount.core :refer [defstate]]
            [muuntaja.core :as m]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [zdl.lex.env :refer [env]]
            [zdl.lex.server.auth :as auth]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.exception :as exception]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.home :as home]
            [zdl.lex.server.lock :as lock]
            [zdl.lex.server.mantis :as mantis]
            [zdl.lex.server.oxygen :as oxygen]
            [zdl.lex.server.solr :as solr]
            [zdl.lex.server.status :as status]))

(defn wrap-defaults [handler]
  (->> (-> defaults/secure-site-defaults
           (assoc-in [:cookies] false)
           (assoc-in [:session] false)
           (assoc-in [:static] false)
           (assoc-in [:proxy] true)
           (assoc-in [:security :anti-forgery] false)
           (assoc-in [:security :ssl-redirect] false))
       (defaults/wrap-defaults handler)))

(def handler
  (ring/ring-handler
   (ring/router
   [""
    {:coercion spec-coercion/coercion
     :muuntaja m/instance
     :middleware [wrap-defaults
                  muuntaja/format-negotiate-middleware
                  muuntaja/format-response-middleware
                  exception/middleware
                  muuntaja/format-request-middleware
                  coercion/coerce-response-middleware
                  coercion/coerce-request-middleware
                  auth/wrap]}
    article/ring-handlers
    git/ring-handlers
    home/ring-handlers
    lock/ring-handlers
    mantis/ring-handlers
    oxygen/ring-handlers
    solr/ring-handlers
    status/ring-handlers
    ["/assets/**" {:no-doc true
                   :handler (constantly nil)
                   :middleware [wrap-webjars]}]
    ["/swagger.json" {:no-doc true
                      :handler (swagger/create-swagger-handler)}]
    ["/docs/api/*" {:no-doc true
                    :handler (swagger-ui/create-swagger-ui-handler)}]])
   (ring/routes
    (ring/create-default-handler))))

(defstate server
  :start
  (do
    (require 'ring.adapter.jetty)
    ((ns-resolve 'ring.adapter.jetty 'run-jetty)
     handler {:port (env :http-port) :join? false}))
  :stop (.. server (stop)))
