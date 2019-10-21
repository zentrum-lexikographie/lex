(ns zdl-lex-common.util
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.url :refer [path->uri]])
  (:import [java.net URL URLEncoder]
           java.util.UUID))

(def file (comp fs/normalized fs/absolute fs/file))

(defn uuid []
  (-> (UUID/randomUUID) str str/lower-case))

(defn ->clean-map [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))

(defn url-encode [s]
  (.. (URLEncoder/encode s "UTF-8") (replace "+" "%20")))

(defn map->query [m]
  (some->> (seq m)
           (sort)
           (map (fn [[k v]] [(url-encode (name k)) "=" (url-encode (str v))]))
           (interpose "&")
           (flatten)
           (apply str)))

(defn url [base & args]
  (let [base (.. (URL. base) (toURI))
        uri (reduce #(.resolve %1 (path->uri %2)) base (filter string? args))
        url (.. uri (toURL) (toString))
        query (some-> (apply merge (filter map? args)) map->query)]
    (URL. (str/join \? (remove nil? [url query])))))

(def server-url (partial url (env :server-base)))
