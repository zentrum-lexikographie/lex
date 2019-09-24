(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-common.util :refer [uuid]]
            [zdl-lex-client.http :as http])
  (:import [java.net URLConnection URLStreamHandler]
           [ro.sync.exml.plugin.lock LockException LockHandler]))

(def tokens (atom {}))

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
      (let [id (lexurl/url->id url)
            token (or (@tokens id) (uuid))
            lock (http/lock id timeoutSeconds token)]
        (if (http/successful? lock)
          (swap! tokens assoc id token)
          (throw (LockException. "Lock Error!" true (str lock))))))
    (unlock
      [url]
      (let [id (lexurl/url->id url)]
        (when-let [token (@tokens id)]
          (timbre/info (format "Unlock! %s" url))
          (future (http/unlock id token)))))))

(defn -getURLStreamHandler [this protocol]
  (if (= "lex" protocol) http/api-store-lexurl-handler))
