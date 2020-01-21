(ns zdl-lex-client.http
  (:require [clojure.core.async :as a]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.query :as query]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.util :refer [server-url]]
            [zdl-lex-common.xml :as xml])
  (:import [java.io File IOException]
           java.net.ConnectException))

(comment
  (str (server-url "lock/" "test/test2.xml" {:ttl "300"} {:token "_"})))

(defn str->stream [s stream]
  (spit stream s :encoding "UTF-8"))

(defn stream->str [stream]
  (slurp stream :encoding "UTF-8"))

(defn successful? [{:keys [status]}] (< status 400))

(defn handle-on-success [handler response stream]
  (if (successful? response) (handler stream) (stream->str stream)))

(defn- respond [request con handler full-response? body-stream]
  (let [head {:status (.getResponseCode con)
              :message (.getResponseMessage con)
              :headers (->> (into {} (.getHeaderFields con))
                            (remove (comp nil? first))
                            (into (sorted-map)))}
        body (if handler
               (handler head body-stream)
               (stream->str body-stream))
        response (assoc head :body body)]
    (cond
      full-response? response
      (successful? response) body
      :else (throw (ex-info "HTTP Client Error"
                            (assoc request :response response))))))

(let [user (env :server-user)
      password (env :server-password)
      basic-creds (if (and user password)
                    (->> (str user ":" password) (.getBytes)
                         (base64/encode) (map char) (apply str)))]
  (defn request [method url &
                 {:keys [headers request-body response-handler full-response?]
                  :or {headers {} full-response? false}
                  :as options}]
    (let [con (.. url (openConnection))
          output? (or (#{"POST" "PUT"} method) request-body)
          request (merge {:method method :url url} options)
          respond (partial respond request con response-handler full-response?)]
      (timbre/debug request)
      (try
        (.setInstanceFollowRedirects con false)
        (.setRequestMethod con method)
        (when basic-creds
          (.setRequestProperty con "Authorization" (str "Basic " basic-creds)))
        (doseq [[key value] headers]
          (.setRequestProperty con key value))
        (when output?
          (.setDoOutput con true)
          (with-open [request-stream (.getOutputStream con)]
            (when request-body
              (request-body request-stream))))
        (with-open [response-stream (.getInputStream con)]
          (respond response-stream))
        (catch ConnectException e
          (throw (ex-info "HTTP Client Error" request e)))
        (catch IOException e
          (with-open [response-stream (.getErrorStream con)]
            (respond response-stream)))))))

(def xml-response-handler
  (partial handle-on-success xml/->dom))

(defn get-xml [url]
  (request
   "GET" url
   :headers {"Accept" "text/xml"}
   :response-handler xml-response-handler))

(defn post-xml [url xml]
  (request
   "POST" url
   :headers {"Content-Type" "text/xml;charset=utf-8" "Accept" "text/xml"}
   :request-body (partial str->stream xml)
   :response-handler xml-response-handler))

(def edn-response-handler
  (partial handle-on-success (comp read-string stream->str)))

(defn get-edn [url]
  (request
   "GET" url
   :headers {"Accept" "application/edn"}
   :response-handler edn-response-handler))

(defn post-edn [url d]
  (request
   "POST" url
   :headers {"Content-Type" "application/edn" "Accept" "application/edn"}
   :request-body (partial str->stream (pr-str d))
   :response-handler edn-response-handler))

(defn id->store-url [id]
  (server-url "article/" id))

(defn lock [id ttl token]
  (request
   "POST" (server-url "lock/" id {:ttl (str ttl) :token token})
   :headers {"Accept" "application/edn"}
   :response-handler edn-response-handler))

(defn unlock [id token]
  (request
   "DELETE" (server-url "lock/" id {:token token})
   :headers {"Accept" "application/edn"}
   :response-handler edn-response-handler
   :full-response? true))

(defn get-issues [q]
  (get-edn (server-url "/mantis/issues" {:q q})))

(defn suggest-forms [q]
  (get-edn (server-url "/index/forms/suggestions" {:q q})))

(defn create-article [form pos]
  (request
   "PUT" (server-url "/article" {:form form :pos pos})
   :headers {"Accept" "application/edn"}
   :response-handler edn-response-handler))

(defn get-status []
  (->> (get-edn (server-url "/status"))
       (merge {:timestamp (java.time.Instant/now)})
       (bus/publish! :status)))

(defstate ping-status
  :start (cron/schedule "*/30 * * * * ?" "Get server status" get-status)
  :stop (a/close! ping-status))

(defn search-articles [req]
  (let [q (query/translate (req :query))]
    (->> (get-edn (server-url "/index" {:q q :limit "1000"}))
         (merge req)
         (bus/publish! :search-response))))

(defstate search-reqs->responses
  :start (bus/listen :search-request search-articles)
  :stop (search-reqs->responses))

(defn export [query ^File f]
  (let [q (query/translate query)]
    (request
     "GET" (server-url "/index/export" {:q q :limit "50000"})
     :headers {"Accept" "text/csv"}
     :response-handler (partial handle-on-success #(io/copy % f)))))

(comment (search-articles {:query "spitz*"}))
