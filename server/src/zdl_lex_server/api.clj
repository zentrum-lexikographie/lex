(ns zdl-lex-server.api
  (:require [mount.core :refer [defstate]]
            [ring.middleware.defaults :as middleware]
            [ring.logger.timbre :as ring-logger]
            [ring.adapter.jetty :as jetty]
            [zdl-lex-server.env :refer [config]]))

(def middleware-defaults
  (assoc middleware/site-defaults
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
  (->
   (fn [req] {:status 200
              :headers {"Content-Type" "text/plain"}
              :body "Hello World!"})
   (middleware/wrap-defaults middleware-defaults)
   (ring-logger/wrap-with-logger)))

(defstate server
  :start (jetty/run-jetty
          handler (merge (config :jetty-opts) {:join? false}))
  :stop (.stop server))
