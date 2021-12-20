(ns zdl.lex.server.solr.client
  (:require [clojure.core.async :as a]
            [clojure.data.xml :as dx]
            [clojure.tools.logging :as log]
            [lambdaisland.uri :as uri]
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

(defn ->request
  [{:keys [url] :as req}]
  (cond-> req
    :always (assoc :url (str (uri/join base-url url)))
    :always (update :request-method #(or % :get))
    auth    (assoc :basic-auth auth)))

(defn async-request
  [req]
  (let [[response error] (http/async-request req)]
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
  [query-params]
  (let [req (->request {:url "query" :query-params query-params})]
    (a/go
      (when-let [resp (a/<! (async-request req))]
        (http/update-timer! query-timer resp)
        (http/read-json resp)))))

(def update-timer
  (timers/timer ["solr" "client" "update-timer"]))

(defn update!
  [xml-node]
  (->
   {:request-method :post
    :url            "update"
    :query-params   {:wt "json"}
    :headers        {"Content-Type" "text/xml"}
    :body           (dx/emit-str xml-node)}
   (->request)
   (http/request)))

(defn add!
  [docs]
  (update! (dx/sexp-as-element [:add (seq docs)])))

(defn remove!
  [ids]
  (update! (dx/sexp-as-element [:delete (for [id ids] [:id id])])))

(defn optimize!
  []
  (update! (dx/sexp-as-element [:update [:commit] [:optimize]])))

(defn purge!
  [doc-type threshold]
  (update!
   (dx/sexp-as-element
    [:delete
     [:query (format "doc_type:%s && time_l:[* TO %s}"
                     doc-type (.getTime threshold))]])))

(defn clear!
  [doc-type]
  (update!
   (dx/sexp-as-element
    [:delete
     [:query (format "doc_type:%s" doc-type)]])))

