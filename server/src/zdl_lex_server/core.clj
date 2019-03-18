(ns zdl-lex-server.core
  (:require [taoensso.timbre :as timbre]
            [ring.middleware.defaults :as middleware]
            [ring.logger.timbre :as ring-logger]
            [ring.adapter.jetty :as jetty])
  (:import [org.slf4j.bridge SLF4JBridgeHandler])
  (:gen-class))

(SLF4JBridgeHandler/removeHandlersForRootLogger)
(SLF4JBridgeHandler/install)

(timbre/handle-uncaught-jvm-exceptions!)
(timbre/set-level! :info)

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


(defonce server (atom nil))

(defn start-server []
  (reset! server (jetty/run-jetty handler {:port 3000 :join? false})))

(defn stop-server []
  (when-not (nil? @server)
    (.stop @server)
    (reset! server nil)))

(def -main start-server)
