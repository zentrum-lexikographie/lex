(ns zdl.lex.client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [byte-streams :as bs]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [lambdaisland.uri :as uri]
            [manifold.deferred :as d]
            [zdl.lex.client :as client]
            [zdl.lex.client.auth :as auth]
            [zdl.lex.url :as lexurl :refer [server-base url url->id]])
  (:import [java.io ByteArrayOutputStream IOException]
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

(defn handle-lock-errors
  [e]
  (if-let [response (ex-data e)]
    (let [{:keys [status body]} response]
      (if (= 423 status)
        (d/error-deferred (lock->exception (client/decode-edn-response body)))
        (d/error-deferred e)))
    (d/error-deferred e)))

(defn handle-io-errors
  [e]
  (log/warn e "I/O error while (un-)locking resource"))

(defn -isLockingSupported [this protocol]
  (= "lex" protocol))

(defn -getLockHandler [this]
  (proxy [LockHandler] []
    (isLockEnabled
      []
      true)
    (updateLock
      [url timeout]
      (->
       (auth/with-authentication (client/lock-resource (url->id url) timeout))
       (d/chain (constantly nil))
       (d/catch IOException handle-io-errors)
       (d/catch handle-lock-errors)
       (deref)))
    (unlock
      [url]
      (->
       (auth/with-authentication (client/unlock-resource (url->id url)))
       (d/chain (constantly nil))
       (d/catch IOException handle-io-errors)
       (d/catch handle-lock-errors)
       (deref)))))

(defn- lexurl->httpurl
  [u]
  (let [id (url->id u)
        url (uri/join server-base "article/" id)
        url (uri/assoc-query url :token client/*lock-token*)]
    (str url)))

(defn handle-store-response
  [response]
  (->
   response
   (d/chain :body bs/to-byte-array bs/to-input-stream)
   (d/catch handle-lock-errors)
   (d/catch LockException (fn [e] (d/error-deferred (IOException. e))))))

(defn store->stream
  [url]
  (->
   (auth/with-authentication
     (client/request url :get
                     {:headers {"Accept" "text/xml, application/edn"}}))
   (handle-store-response)
   (deref)))

(defn stream->store
  [url]
  (proxy [ByteArrayOutputStream] []
    (close []
      (->
       (auth/with-authentication
         (client/request url :post
                         {:headers {"Content-Type" "text/xml"
                                    "Accept"       "text/xml, application/edn"}
                          :body    (.toByteArray this)}))
       (handle-store-response)
       (deref)))))

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
  [this protocol]
  (when (= "lex" protocol) lexurl-handler))
