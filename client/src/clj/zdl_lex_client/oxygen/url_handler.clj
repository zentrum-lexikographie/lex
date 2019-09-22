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

(defn -isLockingSupported [this protocol]
  (= lexurl/protocol protocol))

(defn -getLockHandler [this]
  (proxy [LockHandler] []
    (isLockEnabled [] true)
    (unlock [url]
      (timbre/info (format "Unlock! %s" url)))
    (updateLock [url timeoutSeconds]
      (timbre/info (format "Lock! %s (%d s)" url timeoutSeconds)))))

(defn -getURLStreamHandler [this protocol]
  (if (= lexurl/protocol protocol) http/webdav-lexurl-handler))
