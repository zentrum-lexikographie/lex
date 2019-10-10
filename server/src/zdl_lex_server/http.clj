(ns zdl-lex-server.http
  (:require [clojure.data.codec.base64 :as base64]
            [mount.core :refer [defstate]]
            [muuntaja.middleware :refer [wrap-format wrap-params]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.logger.timbre :refer [wrap-with-logger]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.http-response :as htstatus]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-server.article :as article]
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

(defn- decode [^String b64-s]
  (-> (.getBytes b64-s "UTF-8") (base64/decode) (String.)))

(let [anonymous-user (env :http-anon-user)]
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

(def base-middleware
  (concat [#(wrap-defaults % defaults)] (if (env :http-log) [wrap-with-logger])))

(def handler
  (ring/ring-handler
   (ring/router
    [[""
      {:middleware [wrap-params wrap-format wrap-auth]}
      article/ring-handlers
      git/ring-handlers
      home/ring-handlers
      lock/ring-handlers
      mantis/ring-handlers
      oxygen/ring-handlers
      solr/ring-handlers
      status/ring-handlers]])
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (wrap-content-type (wrap-webjars (constantly nil)))
    (ring/create-default-handler))
   {:middleware base-middleware}))

(defstate server
  :start (jetty/run-jetty handler {:port (env :http-port) :join? false})
  :stop (.. server (stop)))

(comment
  (mount/start #'server)
  (mount/stop))
