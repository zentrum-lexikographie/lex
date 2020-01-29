(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [byte-streams :as bs]
            [manifold.deferred :as d]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-common.util :as util])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream IOException]
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
      (timbre/info (format "Lock! %s (%d s)" url timeoutSeconds))
      @(http/lock (lexurl/url->id url) timeoutSeconds token))
    (unlock
      [url]
      (timbre/info (format "Unlock! %s" url))
      (http/unlock (lexurl/url->id url) token))))

(defn- lexurl->httpurl
  [url]
  (-> url
      (lexurl/url->id) (http/id->store-url)
      (str) (util/url {:token token})))

(defn lock->io-exception
  [d]
  (d/catch d LockException #(d/error-deferred (IOException. %))))

(defn store->stream
  [url]
  (->
   (http/request {:request-method :get :url url
                  :accept "text/xml, application/edn"})
   (d/chain :body bs/to-byte-array #(ByteArrayInputStream. %))
   (lock->io-exception)
   (deref)))

(defn stream->store
  [url]
  (proxy [ByteArrayOutputStream] []
    (close []
      (->
       (http/request {:request-method :post :url url
                      :content-type "text/xml"
                      :body (.toString this "UTF-8")
                      :accept "text/xml, application/edn"})
       (d/chain :body bs/to-byte-array #(ByteArrayInputStream. %))
       (lock->io-exception)
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
