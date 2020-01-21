(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-common.util :as util])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.net URLConnection URLStreamHandler]
           [ro.sync.exml.plugin.lock LockException LockHandler]))

(def token (util/uuid))

(defn -isLockingSupported [this protocol]
  (= "lex" protocol))

(def ^:private readable-date-time-formatter
  (java.time.format.DateTimeFormatter/ofPattern "dd.MM.YYYY', 'HH:mm' Uhr'"))

(defn lock->exception [{:keys [resource owner owner_ip expires]}]
  (let [owner (format "%s (IP: %s)" owner owner_ip)
        until (.. (java.time.Instant/ofEpochMilli expires)
                  (atZone (java.time.ZoneId/systemDefault))
                  (format readable-date-time-formatter))
        message (->> ["Artikel gesperrt"
                      ""
                      (format "Pfad: %s" resource)
                      (format "Von: %s" owner)
                      (format "Ablaufdatum: %s" until)
                      ""]
                     (str/join \newline))]
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
            lock (http/lock id timeoutSeconds token)]
        (if-not (= token (:token lock))
          (throw (lock->exception lock)))))
    (unlock
      [url]
      (let [id (lexurl/url->id url)]
        (timbre/info (format "Unlock! %s" url))
        (future (http/unlock id token))))))

(defn- lexurl->httpurl
  [url]
  (-> url
      (lexurl/url->id) (http/id->store-url)
      (str) (util/url {:token token})))

(defn store->stream
  [url]
  (let [xml (http/request "GET" url :headers {"Accept" "text/xml"})]
    (ByteArrayInputStream. (.getBytes xml "UTF-8"))))

(defn stream->store
  [url]
  (proxy [ByteArrayOutputStream] []
    (close []
      (http/post-xml url (.toString this "UTF-8")))))

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
