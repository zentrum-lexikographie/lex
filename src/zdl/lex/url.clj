(ns zdl.lex.url
  (:require [clojure.string :as str]
            [zdl.lex.env :refer [getenv]])
  (:import [java.net URI URL URLEncoder URLStreamHandler URLStreamHandlerFactory]))

(defn url-encode
  [s]
  (.. (URLEncoder/encode s "UTF-8") (replace "+" "%20")))

(defn map->query
  [m]
  (some->>
   (seq m)
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
  (getenv "SERVER_URL" "https://lex.dwds.de/"))

(defn server-url
  [& args]
  (apply url server-base args))

(def url-base
  (str/replace server-base #"[^:]+://" "lex://"))

(defn lex? [^URL u]
  (str/starts-with? (str u) url-base))

(defn id->url [id]
  (.. (URL. url-base) (toURI) (resolve (path->uri id)) (toURL)))

(defn url->id [^URL u]
  (if (lex? u) (.. (URL. url-base) (toURI)
                   (relativize (.. u (toURI))) (getPath))))

(defn install-stream-handler!
  ([]
   (install-stream-handler! (proxy [URLStreamHandler] [])))
  ([handler]
   (URL/setURLStreamHandlerFactory
    (proxy [URLStreamHandlerFactory] []
      (createURLStreamHandler [protocol]
        (if (= "lex" protocol) handler))))))

(comment
  (install-stream-handler!)
  (-> "WDG/ve/Verfasserkollektiv-E_k_6565.xml" id->url url->id)
  (id->url "test.xml")
  (.. (URL. "http://test.com/") (toURI) (resolve "?q=2") (resolve "?p=1") (toURL)))
