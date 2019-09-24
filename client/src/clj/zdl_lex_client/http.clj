(ns zdl-lex-client.http
  (:require [clojure.core.async :as a]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.query :as query]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-common.util :refer [url]]
            [zdl-lex-common.xml :as xml])
  (:import [java.io File IOException]
           [java.net ConnectException URI URL URLStreamHandler URLConnection]))

(def server-url (partial url (env :server-base)))

(comment
  (str (server-url "lock/" "test/test2.xml" {:ttl "300"} {:token "_"})))

(let [user (env :server-user)
      password (env :server-password)
      basic-creds (if (and user password)
                    (->> (str user ":" password) (.getBytes)
                         (base64/encode) (map char) (apply str)))]

  (defn- parse-response [con body-stream & {:keys [response-handler]}]
    (let [response {:status (.getResponseCode con)
                    :message (.getResponseMessage con)
                    :headers (into (sorted-map) (.getHeaderFields con))}]
      (->> (if response-handler
             (response-handler response body-stream)
             (slurp body-stream :encoding "UTF-8"))
           (assoc response :body))))

  (defn request [method url &
                 {:keys [content-type accept request-body] :as options}]
    (timbre/debug (merge {:method method :url url} options))
    (let [con (.. url (openConnection))]
      (try
        (.setRequestMethod con method)
        (when basic-creds
          (.setRequestProperty con "Authorization" (str "Basic " basic-creds)))
        (when content-type
          (.setRequestProperty con "Content-Type" content-type))
        (when accept
          (.setRequestProperty con "Accept" accept))
        (when request-body
          (.setDoOutput con true)
          (with-open [request-stream (.getOutputStream con)]
            (request-body request-stream)))
        (with-open [response-stream (.getInputStream con)]
          (parse-response con response-stream options))
        (catch ConnectException e
          (throw (ex-info "I/O error while connecting to XML database" {} e)))
        (catch IOException e
          (with-open [response-stream (.getErrorStream con)]
            (parse-response con response-stream options)))))))

(defn read-xml [con]
  (doto con (.setRequestProperty "Accept" "text/xml"))
  (.getInputStream con))

(def get-xml (partial tx (comp xml/->dom read-xml)))

(defn write-and-read-xml [msg con]
  (doto con
    (.setDoOutput true)
    (.setRequestMethod "POST")
    (.setRequestProperty "Content-Type" "text/xml;charset=utf-8")
    (.setRequestProperty "Accept" "text/xml"))
  (-> (.getOutputStream con) (spit msg :encoding "UTF-8"))
  (-> (.getInputStream con) (xml/->dom)))

(defn- read-edn
  ([con]
   (read-edn "GET" con))
  ([method con]
   (doto con
     (.setRequestMethod (str/upper-case method))
     (.setRequestProperty "Accept" "application/edn"))
   (-> (.getInputStream con) (slurp :encoding "UTF-8") (read-string))))

(defn- write-and-read-edn [msg con]
  (doto con
    (.setDoOutput true)
    (.setRequestMethod "POST")
    (.setRequestProperty "Content-Type" "application/edn")
    (.setRequestProperty "Accept" "application/edn"))
  (-> (.getOutputStream con) (spit (pr-str msg) :encoding "UTF-8"))
  (-> (.getInputStream con) (slurp :encoding "UTF-8") (read-string)))

(def get-edn
  (partial tx read-edn))

(defn post-edn [url msg]
  (tx (partial write-and-read-edn msg) url))

(def api-store-lexurl-handler
  (proxy [URLStreamHandler] []
    (openConnection [url]
      (let [api-store-url (server-url "store/" (lexurl/url->id url))]
        (proxy [URLConnection] [url]
          (connect
            []
            (comment "No-Op"))
          (getInputStream
            []
            (tx read-xml api-store-url))
          (getOutputStream
            []
            (proxy [java.io.ByteArrayOutputStream] []
              (close []
                (let [xml (.. this (toString "UTF-8"))]
                  (tx (partial write-and-read-xml xml) api-store-url))))))))))

(defn lock [id ttl token]
  (->> (if token {:token token} {})
       (server-url "lock/" id {:ttl (str ttl)})
       (post-edn)))

(defn unlock [id token]
  (tx (partial read-edn "DELETE") (server-url "lock/" id {:token token})))

(defn get-issues [q]
  (get-edn (server-url "/articles/issues" {:q q})))

(defn suggest-forms [q]
  (get-edn (server-url "/articles/forms/suggestions" {:q q})))

(defn create-article [form pos]
  (post-edn (server-url "/articles/create" {:form form :pos pos}) {}))

(defn get-status []
  (->> (get-edn (server-url "/status"))
       (merge {:timestamp (t/now)})
       (bus/publish! :status)))

(defstate ping-status
  :start (cron/schedule "*/30 * * * * ?" "Get server status" get-status)
  :stop (a/close! ping-status))

(defn search-articles [req]
  (let [q (query/translate (req :query))]
    (->> (get-edn (server-url "/articles/search" {:q q :limit "1000"}))
         (merge req)
         (bus/publish! :search-response))))

(defstate search-reqs->responses
  :start (bus/listen :search-request search-articles)
  :stop (search-reqs->responses))

(defn save-csv-to-file [^File f]
  (fn [con]
    (doto con
      (.setRequestProperty "Accept" "text/csv"))
    (io/copy (.getInputStream con) (io/output-stream f))))

(defn export [query ^File f]
  (let [q (query/translate query)]
    (tx (save-csv-to-file f)
        (server-url "/articles/export" {:q q :limit "50000"}))))

