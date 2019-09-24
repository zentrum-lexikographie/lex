(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-client.http :as http])
  (:import [java.net URLConnection URLStreamHandler]
           ro.sync.exml.plugin.lock.LockHandler))

(def tokens (atom {}))

(defn -isLockingSupported [this protocol]
  (= "lex" protocol))

(defn -getLockHandler [this]
  (proxy [LockHandler] []
    (isLockEnabled [] true)
    (unlock [url]
      (timbre/info (format "Unlock! %s" url))
      (let [id (lexurl/url->id url)]
        (when-let [token (@tokens id)]
          (future
            (http/unlock id token)
            (swap! tokens dissoc idx)))))
    (updateLock [url timeoutSeconds]
      (timbre/info (format "Lock! %s (%d s)" url timeoutSeconds))
      (let [id (lexurl/url->id url)]
        (let [token (@tokens id)]
          (http/lock id timeoutSeconds token))))))

(defn -getURLStreamHandler [this protocol]
  (if (= "lex" protocol) http/api-store-lexurl-handler))
