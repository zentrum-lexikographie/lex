(ns zdl-lex-common.url
  (:require [clojure.string :as str]
            [zdl-lex-common.env :refer [env]])
  (:import [java.net URL URI URLStreamHandler URLStreamHandlerFactory]))

(def base (str/replace (env :server-base) #"[^:]+://" "lex://"))

(defn lex? [^URL u]
  (str/starts-with? (str u) base))

(defn path->uri [path] (URI. nil nil path nil))

(defn id->url [id]
  (.. (URL. base) (toURI) (resolve (path->uri id)) (toURL)))

(defn url->id [^URL u]
  (if (lex? u) (.. (URL. base) (toURI) (relativize (.. u (toURI))) (getPath))))

(let [noop (proxy [URLStreamHandler] [])]
  (defn install-stream-handler!
    ([]
     (install-stream-handler! noop))
    ([handler]
     (URL/setURLStreamHandlerFactory
      (proxy [URLStreamHandlerFactory] []
        (createURLStreamHandler [protocol]
          (if (= "lex" protocol) handler)))))))

(comment
  (install-stream-handler!)
  (-> "WDG/ve/Verfasserkollektiv-E_k_6565.xml" id->url url->id)
  (id->url "test.xml")
  (.. (URL. "http://test.com/") (toURI) (resolve "?q=2") (resolve "?p=1") (toURL)))
