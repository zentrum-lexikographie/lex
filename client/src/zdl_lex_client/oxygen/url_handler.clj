(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-common.util :as util]
            [manifold.deferred :as d]
            [byte-streams :as bs])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream IOException]
           [java.net URLConnection URLStreamHandler]
           [ro.sync.exml.plugin.lock LockException LockHandler]))

(def token (util/uuid))

(defn -isLockingSupported [this protocol]
  (= "lex" protocol))

(def ^:private readable-date-time-formatter
  (java.time.format.DateTimeFormatter/ofPattern "dd.MM.YYYY', 'HH:mm' Uhr'"))

(defn lock->owner
  [{:keys [owner owner_ip]}]
  (format "%s (IP: %s)" owner owner_ip))

(defn lock->message
  [{:keys [resource expires] :as lock}]
  (let [path (or (not-empty resource) "<alle>")
        owner (lock->owner lock)
        until (.. (java.time.Instant/ofEpochMilli expires)
                  (atZone (java.time.ZoneId/systemDefault))
                  (format readable-date-time-formatter))]
    (->> ["Artikel gesperrt"
          ""
          (format "Pfad: %s" path)
          (format "Von: %s" owner)
          (format "Ablaufdatum: %s" until)
          ""]
         (str/join \newline))))

(defn lock->exception [lock]
  (let [owner (lock->owner lock)
        message (lock->message lock)]
    (doto (LockException. message true message) (.setOwnerName owner))))

(defn -getLockHandler [this]
  (proxy [LockHandler] []
    (isLockEnabled
      []
      true)
    (updateLock
      [url timeoutSeconds]
      (timbre/info (format "Lock! %s (%d s)" url timeoutSeconds))
      (let [id (lexurl/url->id url)
            lock @(http/lock id timeoutSeconds token)]
        (if-not (= token (:token lock))
          (throw (lock->exception lock)))))
    (unlock
      [url]
      (let [id (lexurl/url->id url)]
        (timbre/info (format "Unlock! %s" url))
        (http/unlock id token)))))

(defn- lexurl->httpurl
  [url]
  (-> url
      (lexurl/url->id) (http/id->store-url)
      (str) (util/url {:token token})))

(defn handle-store-response
  [{:keys [status body] :as response}]
  (condp = status
    200 (ByteArrayInputStream. (bs/to-byte-array body))
    423 (->>
         (bs/to-string body)
         (edn/read-string)
         (lock->message)
         (IOException.)
         (d/error-deferred))
    (d/error-deferred (IOException. (ex-info response)))))

(defn store->stream
  [url]
  (->
   (http/request {:request-method :get :url url
                  :accept "text/xml, application/edn"
                  :throw-exceptions false})
   (d/chain handle-store-response)
   (deref)))

(defn stream->store
  [url]
  (proxy [ByteArrayOutputStream] []
    (close []
      (->
       (http/request {:request-method :post :url url
                      :content-type "text/xml"
                      :body (.toString this "UTF-8")
                      :accept "text/xml, application/edn"
                      :throw-exceptions false})
       (d/chain handle-store-response)
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


(defn -getURLStreamHandler [this protocol]
  (if (= "lex" protocol) lexurl-handler))

(comment
  (http/lock "" 300 token)
  (http/unlock "" token))
