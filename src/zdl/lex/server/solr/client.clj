(ns zdl.lex.server.solr.client
  (:require
   [clojure.core.async :as a]
   [clojure.tools.logging :as log]
   [gremid.data.xml :as dx]
   [lambdaisland.uri :as uri]
   [metrics.timers :as timers]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.http :as http])
  (:import
   (java.util.concurrent TimeUnit)))

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
    :body           (dx/emit-str (dx/sexp-as-element [:-document xml-node]))}
   (->request)
   (http/request)))

(defn log-update
  [action docs]
  (log/debugf "%s %d doc(s)" action (count docs))
  docs)

(def add-xf
  (comp
   (partition-all 10000)
   (map (partial log-update "Updating"))
   (map (fn [docs] [:add (seq docs)]))
   (map update!)))

(defn add!
  [docs]
  (into [] add-xf docs))

(def removal-xf
  (comp
   (partition-all 10000)
   (map (partial log-update "Removing"))
   (map (fn [ids] [:delete (for [id ids] [:id id])]))
   (map update!)))

(defn remove!
  [ids]
  (into [] removal-xf ids))

(defn optimize!
  []
  (update!
   [:update [:commit] [:optimize]]))

(defn purge!
  [doc-type threshold]
  (log/debugf "Purging %s(s) before %s" doc-type threshold)
  (update!
   [:delete
    [:query (format "doc_type:%s && time_l:[* TO %s}"
                    doc-type (.getTime threshold))]]))

(defn clear!
  [doc-type]
  (update!
   [:delete
    [:query (format "doc_type:%s" doc-type)]]))

