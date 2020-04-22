(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [zdl-lex-client.http :as http]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-common.util :as util])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream InputStream IOException]
           [java.net URLConnection URLStreamHandler]
           [ro.sync.exml.plugin.lock LockException LockHandler]))

(def token (util/uuid))

(defn -isLockingSupported [this protocol]
  (= "lex" protocol))

(defn -getLockHandler [this]
  (proxy [LockHandler] []
    (isLockEnabled
      []
      true)
    (updateLock
      [url timeoutSeconds]
      (try
        (log/infof "Lock! %s (%d s)" url timeoutSeconds)
        (http/lock (lexurl/url->id url) timeoutSeconds token)
        (catch IOException e (log/warn e))))
    (unlock
      [url]
      (try
        (log/infof "Unlock! %s" url)
        (http/unlock (lexurl/url->id url) token)
        (catch IOException e (log/warn e))))))

(defn- lexurl->httpurl
  [url]
  (-> url
      (lexurl/url->id) (http/id->store-url)
      (str) (util/url {:token token})))

(defn in->buf-stream
  [^InputStream in]
  (let [buf (ByteArrayOutputStream.)]
    (io/copy in buf)
    (ByteArrayInputStream. (.toByteArray buf))))

(defn store->stream
  [url]
  (try
    (let [req {:request-method :get :url url
               :accept "text/xml, application/edn"
               :as :stream}]
      (with-open [body-stream (-> req http/request :body)]
        (in->buf-stream body-stream)))
    (catch LockException e
      (throw (IOException. e)))))

(defn stream->store
  [url]
  (proxy [ByteArrayOutputStream] []
    (close []
      (try
        (let [req {:request-method :post :url url
                   :content-type "text/xml"
                   :body (.toString this "UTF-8")
                   :accept "text/xml, application/edn"
                   :as :stream}]
          (with-open [body-stream (-> req http/request :body)]
            (in->buf-stream body-stream)))
        (catch LockException e
          (throw (IOException. e)))))))

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
