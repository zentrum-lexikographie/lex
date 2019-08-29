(ns zdl-lex-server.http
  (:require [clojure.data.codec.base64 :as base64]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]
            [muuntaja.middleware :refer [wrap-format wrap-params]]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [ring.logger.timbre :refer [wrap-with-logger]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.http-response :as htstatus]
            [zdl-lex-server.article :as article]
            [zdl-lex-server.exist :as exist]
            [zdl-lex-server.home :as home]
            [zdl-lex-server.mantis :as mantis]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.status :as status]
            [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [clojure.string :as str]))

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

(defn assoc-auth [{:keys [headers] :as request}]
  (if-let [auth (some->
                 (get-in request [:headers "authorization"])
                 (str/replace #"^Basic " "") (decode) (str/split #":" 2))]
    (assoc request ::user (first auth) ::password (second auth))
    request))

(defn wrap-auth
  [handler]
  (fn
    ([request]
     (handler (assoc-auth request)))
    ([request respond raise]
     (handler (assoc-auth request) respond raise))))

(def base-middleware
  (concat [#(wrap-defaults % defaults)]
          (if (env :zdl-lex-http-log) [wrap-with-logger])))

(def handler
  (ring/ring-handler
   (ring/router
    [[""
      {:middleware [wrap-params wrap-format wrap-auth]}
      ["/" {:get (fn [_] (htstatus/temporary-redirect "/home"))}]
      ["/articles"
       ["/create" {:post article/handle-creation}]
       ["/exist"
        ["/sync-id" {:post exist/handle-article-sync}]
        ["/sync-last/:amount/:unit" {:post exist/handle-period-sync}]]
       ["/export" {:get solr/handle-export}]
       ["/forms/suggestions" {:get solr/handle-form-suggestions}]
       ["/index" {:delete solr/handle-index-trigger}]
       ["/issues" {:get mantis/handle-issue-lookup}]
       ["/search" {:get solr/handle-search}]]
      ["/home" {:get home/handle}]
      ["/status" {:get status/handle-req}]]])
   (ring/routes
    (ring/create-resource-handler {:path "/"})
    (wrap-content-type (wrap-webjars (constantly nil)))
    (ring/create-default-handler))
   {:middleware base-middleware}))

(defstate server
  :start (jetty/run-jetty handler {:port (-> (env :zdl-lex-http-port "3000")
                                             Integer/parseInt)
                                   :join? false})
  :stop (.stop server))

(comment
  (mount/start #'server)
  (mount/stop))
