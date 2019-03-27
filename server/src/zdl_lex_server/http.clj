(ns zdl-lex-server.http
  (:require [mount.core :refer [defstate]]
            [reitit.ring :as ring]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.logger.timbre :refer [wrap-with-logger]]
            [ring.adapter.jetty :as jetty]
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

(defstate handler
  :start
  (wrap-base
   (ring/ring-handler
    (ring/router
     [[""
       {:middleware [wrap-formats]}
       ["/" {:get (fn [_] (htstatus/temporary-redirect "/statistics"))}]
       ["/api" {:get api/handler}]
       ["/statistics" {:get stats/handler}]]])
    (ring/routes
     (ring/create-resource-handler {:path "/"})
     (wrap-content-type (wrap-webjars (constantly nil)))
     (ring/create-default-handler)))))

(defstate server
  :start (jetty/run-jetty
          handler (merge (config :jetty-opts) {:join? false}))
  :stop (.stop server))

