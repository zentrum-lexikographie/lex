(ns zdl.lex.client.io
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [lambdaisland.uri :as uri]
            [zdl.lex.client.http :as client.http]
            [zdl.lex.url :as lexurl]
            [zdl.lex.util :refer [uuid]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream IOException]
           [java.net URLConnection URLStreamHandler]
           [java.time Instant ZoneId]
           java.time.format.DateTimeFormatter
           [ro.sync.exml.plugin.lock LockException LockHandler]))

(defn lock->owner
  [{:keys [owner owner_ip]}]
  (format "%s (IP: %s)" owner owner_ip))

(def ^:private readable-date-time-formatter
  (DateTimeFormatter/ofPattern "dd.MM.YYYY', 'HH:mm' Uhr'"))

(defn lock->message
  [{:keys [resource expires] :as lock}]
  (let [path  (or (not-empty resource) "<alle>")
        owner (lock->owner lock)
        until (.. (Instant/ofEpochMilli expires)
                  (atZone (ZoneId/systemDefault))
                  (format readable-date-time-formatter))]
    (str/join \newline
              ["Artikel gesperrt"
               ""
               (format "Pfad: %s" path)
               (format "Von: %s" owner)
               (format "Ablaufdatum: %s" until)
               ""])))

(defn lock->exception
  [lock]
  (let [owner   (lock->owner lock)
        message (lock->message lock)]
    (doto (LockException. message true message)
      (.setOwnerName owner))))

(def lock-token
  (uuid))

(defn http-request
  [request]
  (->
   request
   (assoc-in [:query-params :token] (str lock-token))
   (assoc :unexceptional-status #{200 423})
   (client.http/request)))

(defn locked?
  [{:keys [status]}]
  (= 423 status))

(defn -isLockingSupported
  [_ protocol]
  (= "lex" protocol))

(defn -getLockHandler
  [_]
  (proxy [LockHandler] []
    (isLockEnabled
      []
      true)
    (updateLock
      [url timeout]
      (try
        (let [id       (lexurl/url->id (uri/uri url))
              request  {:method       :post
                        :url          (uri/join "lock/" id)
                        :query-params {:ttl (str timeout)}}
              response (http-request request)]
          (when (locked? response)
            (throw (lock->exception (response :body)))))
        (catch IOException e
          (log/warn e "I/O error while locking resource"))))
    (unlock
      [url]
      (try
        (let [id       (lexurl/url->id (uri/uri url))
              request  {:method :delete
                        :url    (uri/join "lock/" id)}
              response (http-request request)]
          (when (locked? response)
            (throw (lock->exception (response :body)))))
        (catch IOException e
          (log/warn e "I/O error while unlocking resource"))))))

(defn- lexurl->httpurl
  [u]
  (str (uri/join lexurl/server-base "article/" (lexurl/url->id (uri/uri u)))))

(defn handle-store-response
  [{:keys [body] :as response}]
  (when (locked? response)
    (throw (IOException. (lock->exception body))))
  (ByteArrayInputStream. (.getBytes ^String body "UTF-8")))

(defn store->stream
  [url]
  (let [request {:method  :get
                 :url     url
                 :headers {"Accept" "text/xml, application/edn"}
                 :as      "UTF-8"}]
    (handle-store-response (http-request request))))

(defn stream->store
  [url]
  (proxy [ByteArrayOutputStream] []
    (close []
      (let [request {:method  :post
                     :url     url
                     :headers {"Content-Type" "text/xml"
                               "Accept"       "text/xml, application/edn"}
                     :as      "UTF-8"
                     :body    (.toByteArray this)}]
        (handle-store-response (http-request request))))))

(def lexurl-handler
  (proxy [URLStreamHandler] []
    (openConnection [url]
      (let [http-url (lexurl->httpurl url)]
        (proxy [URLConnection] [url]
          (connect
            []
            (comment "No-Op"))
          (getInputStream
            []
            (store->stream http-url))
          (getOutputStream
            []
            (stream->store http-url)))))))


(defn -getURLStreamHandler
  [_ protocol]
  (when (= "lex" protocol) lexurl-handler))
