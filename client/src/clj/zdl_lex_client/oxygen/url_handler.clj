(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.core.cache.wrapped :as cache]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-common.util :refer [uuid]]
            [zdl-lex-client.http :as http]
            [tick.alpha.api :as t])
  (:import [java.net URLConnection URLStreamHandler]
           [ro.sync.exml.plugin.lock LockException LockHandler]))

(def tokens (cache/basic-cache-factory {}))

(defn -isLockingSupported [this protocol]
  (= "lex" protocol))

(defn lock->exception [{:keys [resource owner owner_ip expires]}]
  (let [owner (format "%s (IP: %s)" owner owner_ip)
        until (t/format "dd.MM.YYYY', 'HH:mm' Uhr'"
                        (t/offset-date-time (t/instant expires)))
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
            token (cache/lookup-or-miss tokens id (fn [_] (uuid)))
            lock (http/lock id timeoutSeconds token)]
        (when-not (= token (:token lock))
          (cache/evict tokens id)
          (throw (lock->exception lock)))))
    (unlock
      [url]
      (let [id (lexurl/url->id url)]
        (when-let [token (cache/lookup tokens id)]
          (timbre/info (format "Unlock! %s" url))
          (cache/evict tokens id)
          (future (http/unlock id token)))))))

(defn -getURLStreamHandler [this protocol]
  (if (= "lex" protocol) http/api-store-lexurl-handler))
