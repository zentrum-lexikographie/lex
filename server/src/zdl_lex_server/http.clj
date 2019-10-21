(ns zdl-lex-server.http
  (:require [clojure.data.codec.base64 :as base64]
            [mount.core :refer [defstate]]
            [muuntaja.core :as m]
            [muuntaja.format.core :as m-format]
            [reitit.coercion.spec :as spec-coercion]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.logger.timbre :refer [wrap-with-logger]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.http-response :as htstatus]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-server.article :as article]
            [zdl-lex-server.csv :as csv]
            [zdl-lex-server.exception :as exception]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.home :as home]
            [zdl-lex-server.lock :as lock]
            [zdl-lex-server.mantis :as mantis]
            [zdl-lex-server.oxygen :as oxygen]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.status :as status]
            [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [zdl-lex-server.git :as git]))

(defn wrap-defaults [handler]
  (->> (-> defaults/secure-site-defaults
           (assoc-in [:cookies] false)
           (assoc-in [:session] false)
           (assoc-in [:static] false)
           (assoc-in [:proxy] true)
           (assoc-in [:security :anti-forgery] false)
           (assoc-in [:security :ssl-redirect] false))
       (defaults/wrap-defaults handler)))

(defn wrap-logger [handler]
  (if (env :http-log) (wrap-with-logger handler) handler))

(let [anonymous-user (env :http-anon-user)
      decode #(-> (.getBytes ^String % "UTF-8") (base64/decode) (String.))]
  (defn assoc-auth [{:keys [headers] :as request}]
    (let [auth (some-> (get-in request [:headers "authorization"])
                       (str/replace #"^Basic " "") (decode) (str/split #":" 2))]
      (assoc request
             ::user (or (first auth) anonymous-user)
             ::password (second auth)))))

(defn wrap-auth
  [handler]
  (fn
    ([request]
     (handler (assoc-auth request)))
    ([request respond raise]
     (handler (assoc-auth request) respond raise))))

(def handler
  (ring/ring-handler
   (ring/router
   [""
    {:coercion spec-coercion/coercion
     :muuntaja m/instance
     :middleware [wrap-logger
                  wrap-defaults
                  wrap-auth
                  muuntaja/format-negotiate-middleware
                  muuntaja/format-response-middleware
                  exception/middleware
                  muuntaja/format-request-middleware
                  coercion/coerce-response-middleware
                  coercion/coerce-request-middleware]}
    article/ring-handlers
    git/ring-handlers
    home/ring-handlers
    lock/ring-handlers
    mantis/ring-handlers
    oxygen/ring-handlers
    solr/ring-handlers
    status/ring-handlers
    ["/assets/**" {:get {:handler (constantly nil)}
                   :middleware [wrap-webjars]}]
    ["/swagger.json" {:no-doc true
                      :get (swagger/create-swagger-handler)}]
    ["/docs/api/*" {:no-doc true
                    :get (swagger-ui/create-swagger-ui-handler)}]])
   (ring/routes
    (ring/create-default-handler))))

(defstate server
  :start
  (do
    (require 'ring.adapter.jetty)
    ((ns-resolve 'ring.adapter.jetty 'run-jetty)
     handler {:port (env :http-port) :join? false}))
  :stop (.. server (stop)))
