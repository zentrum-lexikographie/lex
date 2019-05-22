(ns zdl-lex-server.http
  (:require [mount.core :refer [defstate]]
            [org.httpkit.server :as httpkit]
            [reitit.ring :as ring]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.logger.timbre :refer [wrap-with-logger]]
            [ring.util.http-response :as htstatus]
            [muuntaja.middleware :refer [wrap-format wrap-params]]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.api :as api]
            [zdl-lex-server.stats :as stats]
            [zdl-lex-server.store :as store]))

(defn wrap-base [handler]
  (-> handler
      (wrap-defaults (assoc site-defaults
                            :proxy true
                            :cookies false
                            :params {:keywordize true
                                     :multipart true
                                     :urlencoded true}
                            :responses {:absolute-redirects true
                                        :content-types true
                                        :default-charset "utf-8"
                                        :not-modified-responses true}
                            :sessions false
                            :security false))
      wrap-with-logger))

(defn wrap-formats [handler]
  (let [wrapped (-> handler wrap-params wrap-format)]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(def handler
  (wrap-base
   (ring/ring-handler
    (ring/router
     [[""
       {:middleware [wrap-formats]}
       ["/" {:get (fn [_] (htstatus/temporary-redirect "/statistics"))}]
       ["/articles/forms/suggestions" {:get api/form-suggestions}]
       ["/articles/search" {:get api/search}]
       ["/statistics" {:get stats/handler}]
       ["/status" {:get api/status}]]])
    (ring/routes
     (ring/create-resource-handler {:path "/"})
     (wrap-content-type (wrap-webjars (constantly nil)))
     (ring/create-default-handler)))))

(defstate server
  :start (httpkit/run-server handler (config :http-server-opts))
  :stop (server))

