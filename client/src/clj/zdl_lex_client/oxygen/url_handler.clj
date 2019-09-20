(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http])
  (:import [java.net URLConnection URLStreamHandler]
           ro.sync.exml.plugin.lock.LockHandlerBase))

(def lex-protocol "lex")

(defn -isLockingSupported [this protocol]
  (str/starts-with? protocol lex-protocol))

(defn -getLockHandler [this]
  (proxy [LockHandlerBase] []
    (isLockEnabled []
      true)
    (isSaveAllowed [url timeoutSeconds]
      true)
    (unlock [url])
    (updateLock [url timeoutSeconds])))

(defn -getURLStreamHandler [this protocol]
  (if (str/starts-with? protocol lex-protocol)
    http/webdav-lexurl-handler))
