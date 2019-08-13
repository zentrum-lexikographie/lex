(ns zdl-lex-server.http
  (:require [mount.core :refer [defstate]]
            [muuntaja.middleware :refer [wrap-format wrap-params]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.logger.timbre :refer [wrap-with-logger]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.http-response :as htstatus]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.exist :as exist]
            [zdl-lex-server.home :as home]
            [zdl-lex-server.mantis :as mantis]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.status :as status]
            [mount.core :as mount]))

(def defaults
  (assoc site-defaults
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

(def handler
  (ring/ring-handler
   (ring/router
    [[""
      {:middleware [wrap-params wrap-format]}
      ["/" {:get (fn [_] (htstatus/temporary-redirect "/home"))}]
      ["/articles"
       ["/exist"
        ["/sync-id" {:post exist/handle-article-sync}]
        ["/sync-last/:amount/:unit" {:post exist/handle-period-sync}]]
       ["/export" {:get solr/handle-export}]
       ["/forms/suggestions" {:get solr/handle-form-suggestions}]
       ["/index" {:delete solr/handle-index-trigger}]
       ["/issues" {:get mantis/handle-issue-lookup}]
       ["/search" {:get solr/handle-search}]]
      ["/home" {:get home/handle}]
      ["/status" {:get status/handle}]]])
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (wrap-content-type (wrap-webjars (constantly nil)))
    (ring/create-default-handler))
   {:middleware [#(wrap-defaults % defaults) wrap-with-logger]}))

(defstate server
  :start (jetty/run-jetty handler (assoc (config :http-server-opts) :join? false))
  :stop (.stop server))

(comment
  (mount/start #'server)
  (mount/stop))
