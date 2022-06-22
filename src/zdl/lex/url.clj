(ns zdl.lex.url
  (:require [lambdaisland.uri :as uri]
            [zdl.lex.env :as env]
            [clojure.string :as str])
  (:import [java.net URL URLStreamHandler URLStreamHandlerFactory]))

(def server-base
  (uri/uri (get-in env/config [:zdl.lex.client.http/server :url])))

(def url-base
  (assoc server-base :scheme "lex"))

(defn lex?
  [uri]
  (-> uri :scheme #{"lex"}))

(defn id->url
  [id]
  (uri/join url-base id))

(defn url->id
  [uri]
  (when (lex? uri)
    (-> uri :path (str/replace #"^/" ""))))

(defn install-stream-handler!
  ([]
   (install-stream-handler! (proxy [URLStreamHandler] [])))
  ([handler]
   (URL/setURLStreamHandlerFactory
    (proxy [URLStreamHandlerFactory] []
      (createURLStreamHandler [protocol]
        (when (= "lex" protocol) handler))))))

(comment
  (install-stream-handler!)
  (-> "WDG/ve/Verfasserkollektiv-E_k_6565.xml" id->url url->id)
  (id->url "test.xml"))
