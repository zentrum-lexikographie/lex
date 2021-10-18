(ns zdl.lex.server.solr.client
  (:require [clojure.core.async :as a]
            [clojure.data.xml :as dx]
            [clojure.tools.logging :as log]
            [lambdaisland.uri :as uri :refer [uri]]
            [metrics.timers :as timers]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.http :as http])
  (:import java.util.concurrent.TimeUnit))

(def auth
  (let [user     (getenv "SOLR_USER")
        password (getenv "SOLR_PASSWORD")]
    (when (and user password) [user password])))

(def base-url
  (uri/join
   (getenv "SOLR_URL" "http://localhost:8983/solr/")
   (str (getenv "SOLR_CORE" "articles") "/")))

(defn request
  "Solr client request fn with configurable base URL and authentication"
  [{:keys [url] :as req}]
  (let [req              (cond-> req
                           :always (assoc :url (str (uri/join base-url url)))
                           :always (update :request-method #(or % :get))
                           auth    (assoc :basic-auth auth))
        [response error] (http/async-request req)]
    (a/go
      (let [[error? result] (a/alt! response ([v] [false v])
                                    error    ([v] [true v]))]
        (if error?
          (log/warn result (pr-str req))
          result)))))

(defn update-timer!
  [timer {:keys [request-time] :as response}]
  (when request-time
    (timers/update! timer request-time TimeUnit/MILLISECONDS))
  response)

(def query-timer
  (timers/timer ["solr" "client" "query-timer"]))

(defn query
  [params]
  (a/go
    (when-let [resp (a/<! (request {:url (uri/assoc-query* (uri "query") params)}))]
      (http/update-timer! query-timer resp)
      (http/read-json resp))))

(def update-timer
  (timers/timer ["solr" "client" "update-timer"]))

(defn update!
  [xml-node]
  (a/go
    (let [req {:request-method :post
               :url            (uri "update")
               :query-params   {:wt "json"}
               :headers        {"Content-Type" "text/xml"}
               :body           (dx/emit-str xml-node)}]
      (when-let [resp (a/<! (request req))]
        (http/update-timer! update-timer resp)
        (http/read-json resp)))))
