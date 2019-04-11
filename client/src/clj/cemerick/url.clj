(ns cemerick.url
  (:import (java.net URLEncoder URLDecoder))
  (:require [pathetic.core :as pathetic]
            [clojure.string :as str]))

(defn url-encode
  [string]
  (some-> string str (URLEncoder/encode "UTF-8") (.replace "+" "%20")))

(defn url-decode
  ([string] (url-decode string "UTF-8"))
  ([string encoding]
    (some-> string str (URLDecoder/decode encoding))))

(defn map->query
  [m]
  (some->> (seq m)
    sort                     ; sorting makes testing a lot easier :-)
    (map (fn [[k v]]
           [(url-encode (name k))
            "="
            (url-encode (str v))]))
    (interpose "&")
    flatten
    (apply str)))

(defn split-param [param]
  (->
   (str/split param #"=")
   (concat (repeat ""))
   (->>
    (take 2))))

(defn query->map
  [qstr]
  (when (not (str/blank? qstr))
    (some->> (str/split qstr #"&")
      seq
      (mapcat split-param)
      (map url-decode)
      (apply hash-map))))

(defn- port-str
  [protocol port]
  (when (and (not= nil port)
             (not= -1 port)
             (not (and (== port 80) (= protocol "http")))
             (not (and (== port 443) (= protocol "https"))))
    (str ":" port)))

(defn- url-creds
  [username password]
  (when username
    (str username ":" password)))

(defrecord URL
  [protocol username password host port path query anchor]
  Object
  (toString [this]
    (let [creds (url-creds username password)]
      (str protocol "://"
           creds
           (when creds \@)
           host
           (port-str protocol port)
           path
           (when (seq query) (str \? (if (string? query)
                                       query
                                       (map->query query))))
           (when anchor (str \# anchor))))))

(defn- url*
  [url]
  (let [url (java.net.URL. url)
        [user pass] (str/split (or (.getUserInfo url) "") #":" 2)]
    (URL. (.toLowerCase (.getProtocol url))
          (and (seq user) user)
          (and (seq pass) pass)
          (.getHost url)
          (.getPort url)
          (pathetic/normalize (.getPath url))
          (query->map (.getQuery url))
          (.getRef url))))

(defn url
  "Returns a new URL record for the given url string(s).

   The first argument must be a base url — either a complete url string, or
   a pre-existing URL record instance that will serve as the basis for the new
   URL.  Any additional arguments must be strings, which are interpreted as
   relative paths that are successively resolved against the base url's path
   to construct the final :path in the returned URL record.

   This function does not perform any url-encoding.  Use `url-encode` to encode
   URL path segments as desired before passing them into this fn."
  ([url]
    (if (instance? URL url)
      url
      (url* url)))
  ([base-url & path-segments]
    (let [base-url (if (instance? URL base-url) base-url (url base-url))]
      (assoc base-url :path (pathetic/normalize (reduce pathetic/resolve
                                                        (:path base-url)
                                                        path-segments))))))
