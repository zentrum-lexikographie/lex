(ns zdl.lex.corpora.http
  (:require
   [clj-http.client :as hc]
   [clj-http.conn-mgr :as hc.conn-mgr]
   [clojure.java.io :as io]
   [hickory.core :as h]
   [hickory.select :as hs]
   [lambdaisland.uri :as uri]
   [taoensso.telemere :as tm]
   [zdl.lex.auth :as auth]
   [zdl.lex.env :refer [getenv]])
  (:import
   (java.net Authenticator)
   (org.apache.http.impl NoConnectionReuseStrategy)
   (org.apache.http.impl.client HttpClientBuilder)
   (org.slf4j.bridge SLF4JBridgeHandler)))

(SLF4JBridgeHandler/removeHandlersForRootLogger)
(SLF4JBridgeHandler/install)

(tm/set-min-level! nil "org.apache.http.*" :error)

(Authenticator/setDefault
 (auth/create-authenticator (getenv "SOCKS_PROXY_USER" "webmonitor")
                            (getenv "SOCKS_PROXY_PASSWORD" "webmonitor")))

(defn proxy-connection-manager
  []
  (hc.conn-mgr/make-socks-proxied-conn-manager
   (getenv  "SOCKS_PROXY_HOST" "static.233.95.245.188.clients.your-server.de")
   (-> (getenv "SOCKS_PROXY_PORT" "1080") (parse-long))))

(def user-agents
  "Collection of user agent strings from whatismybrowser.com."
  (->
   (io/resource "zdl/lex/corpora/http-user-agents.txt")
   (io/reader :encoding "UTF-8")
   (line-seq) (vec)))

(defn disable-keep-alive
  [^HttpClientBuilder builder _req]
  (.setConnectionReuseStrategy builder NoConnectionReuseStrategy/INSTANCE))

(defn request
  [req]
  (tm/log! {:level :debug :id ::request :msg (req :url)})
  (-> req
      (assoc-in [:headers "User-Agent"] (rand-nth user-agents))
      (assoc :connection-manager (proxy-connection-manager)
             :http-builder-fns [disable-keep-alive]
             :connection-timeout 10000
             :socket-timeout 10000
             :trace-redirects true)
      (hc/request)))

(def parse-html
  (comp h/as-hickory h/parse))

(defn get-request
  [url]
  (request {:method :get :url url}))

(def rss-feed-link-selector
  (hs/and (hs/tag :link)
          (hs/attr :rel #{"alternate"})
          (hs/attr :type #{"application/atom+xml"
                           "application/rss+xml"
                           "application/rdf+xml"
                           "application/xml"})))

(defn get-feed-links
  [url]
  (try
    (let [response   (get-request url)
          url        (or (last (response :trace-redirects)) url)]
      (->> (hs/select rss-feed-link-selector (-> response :body parse-html))
           (into [] (map (fn [{{:keys [href title]} :attrs}]
                           {:url (str (uri/join url href)) :title title})))))
    (catch Throwable _)))
