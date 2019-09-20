(ns zdl-lex-common.url
  (:require [clojure.string :as str]
            [environ.core :refer [env]])
  (:import [java.net URL URLStreamHandlerFactory URLStreamHandler]))

(def ^:private lex-protocol "lex")

(def ^:private lex-host (env :zdl-lex-host "lex.dwds.de"))

(defn lex? [^URL u]
  (and (= lex-protocol (.. u (getProtocol)))
       (= lex-host (.. u (getHost)))))

(defn id->url [id]
  (URL. lex-protocol lex-host (str/replace (str "/" id) "#^/+" "/")))

(defn url->id [^URL u]
  (if (lex? u) (str/replace (.. u (getFile)) #"^/" "")))

(comment
  (-> "WDG/ve/Verfasserkollektiv-E_k_6565.xml" id->url))

(let [noop (proxy [URLStreamHandler] [])]
  (defn install-stream-handler!
    ([]
     (install-stream-handler! noop))
    ([handler]
     (URL/setURLStreamHandlerFactory
      (proxy [URLStreamHandlerFactory] []
        (createURLStreamHandler [protocol]
          (if (= protocol lex-protocol) handler)))))))

(comment
  (install-stream-handler!)
  (id->url "test.xml"))
