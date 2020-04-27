(ns zdl.lex.util
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]
            [zdl.lex.env :refer [getenv]])
  (:import java.io.File
           [java.net URI URL URLEncoder]
           java.nio.file.Path
           java.util.UUID))

(defn ^File file
  [& args]
  (-> (apply fs/file args) fs/absolute fs/normalized))

(defn ^String fpath
  [& args]
  (.getPath (apply file args)))

(defn ^Path path
  [& args]
  (.toPath (apply file args)))

(defn ^Path relativize
  [base f]
  (.relativize (path base) (path f)))

(defn uuid []
  (-> (UUID/randomUUID) str str/lower-case))

(defn ->clean-map
  [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))

(defn url-encode
  [s]
  (.. (URLEncoder/encode s "UTF-8") (replace "+" "%20")))

(defn map->query
  [m]
  (some->> (seq m)
           (sort)
           (map (fn [[k v]] [(url-encode (name k)) "=" (url-encode (str v))]))
           (interpose "&")
           (flatten)
           (apply str)))

(defn path->uri
  [path]
  (URI. nil nil path nil))

(defn url [base & args]
  (let [base (.. (URL. base) (toURI))
        uri (reduce #(.resolve %1 (path->uri %2)) base (filter string? args))
        url (.. uri (toURL) (toString))
        query (some-> (apply merge (filter map? args)) map->query)]
    (URL. (str/join \? (remove nil? [url query])))))

(def server-base
  (delay (getenv "ZDL_LEX_SERVER_URL" "https://lex.dwds.de/")))

(defn server-url
  [& args]
  (apply url @server-base args))
