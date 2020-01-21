(ns zdl-lex-client.oxygen.url-handler
  (:gen-class
   :name de.zdl.oxygen.URLHandler
   :implements [ro.sync.exml.plugin.urlstreamhandler.URLStreamHandlerWithLockPluginExtension])
  (:require [clojure.core.cache :as cache]
            [clojure.core.memoize :as memo]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-common.util :refer [uuid]])
  (:import [ro.sync.exml.plugin.lock LockException LockHandler]))

(def tokens (memo/lru (fn [_] (uuid)) :lru/threshold 1024))

(defn lookup-token [id]
  (some-> tokens meta ::memo/cache deref (cache/lookup [id]) deref))

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
            token (tokens id)
            lock (http/lock id timeoutSeconds token)]
        (when-not (= token (:token lock))
          (memo/memo-clear! tokens [id])
          (throw (lock->exception lock)))))
    (unlock
      [url]
      (let [id (lexurl/url->id url)]
        (when-let [token (lookup-token id)]
          (timbre/info (format "Unlock! %s" url))
          (memo/memo-clear! tokens [id])
          (future (http/unlock id token)))))))

(defn -getURLStreamHandler [this protocol]
  (if (= "lex" protocol) http/api-store-lexurl-handler))
