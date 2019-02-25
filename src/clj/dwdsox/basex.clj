(ns dwdsox.basex
  (:require [clojure.java.io :as io]
            [clojure.data.codec.base64 :as base64]
            [clojure.data.xml :as xml]
            [clojure.string :as string]
            [taoensso.timbre :as timbre]
            [dwdsox.env :refer [config]])
  (:import [java.io IOException]
           [java.net URL ConnectException]))

(def url (get-in config [:basex :url]))

(def collection (get-in config [:basex :collection]))

(def basic-creds
  (let [{:keys [user password]} (config :basex)]
    (if (and user password)
      (apply str (map char (base64/encode (.getBytes (str user ":" password))))))))

(xml/alias-uri 'bx "http://basex.org/rest")

(defn- with-db-connection [path tx]
  (let [con (-> (str url "/" path) (URL.) (.openConnection))]
    (try
      (when basic-creds
        (.setRequestProperty con "Authorization" (str "Basic " basic-creds)))
      (tx con)
      (catch ConnectException e
        (throw (ex-info "I/O error while connecting to XML database" {} e)))
      (catch IOException e
        (with-open [err (io/reader (.getErrorStream con) :encoding "UTF-8")]
          (throw (ex-info "I/O error while talking to XML database"
                          {:http-status (.getResponseCode con)
                           :http-message (.getResponseMessage con)
                           :http-body (slurp err)})))))))

(defn db-request
  ([path] (db-request path identity))
  ([path request]
   (with-db-connection
     (str "rest/" collection "/" path)
     (fn [con]
       (request con)
       (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
         (slurp resp))))))

(defn db-post-xml
  [path xml-request]
  (db-request
   path
   (fn [con]
     (doto con
       (.setRequestMethod "POST")
       (.setRequestProperty "Content-Type" "application/xml")
       (.setDoOutput true)
       (.setDoInput true))
     (with-open [req (io/writer (.getOutputStream con) :encoding "UTF-8")]
       (xml/emit xml-request req))
     con)))

(defn query [path xquery]
  (timbre/debug (str "? /" path "\n" xquery))
  (db-post-xml path (xml/sexp-as-element
                     [::bx/query {:xmlns/bx "http://basex.org/rest"}
                      [::bx/text [:-cdata xquery]]])))

(defn whoami []
  (query "" "user:current()"))
