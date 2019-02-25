(ns dwdsox.exist-db
  (:require [clojure.java.io :as io]
            [clojure.data.codec.base64 :as base64]
            [clojure.data.xml :as xml]
            [clojure.string :as string]
            [taoensso.timbre :as timbre]
            [dwdsox.env :refer [config]])
  (:import [java.io IOException]
           [java.net URL ConnectException]))

(def url (get-in config [:exist :url]))

(def collection (get-in config [:exist :collection]))

(def basic-creds
  (let [{:keys [user password]} (config :exist)]
    (if (and user password)
      (apply str (map char (base64/encode (.getBytes (str user ":" password))))))))

(xml/alias-uri 'ex "http://exist.sourceforge.net/NS/exist")

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
  ([path] (db-request "webdav" path identity))
  ([type path] (db-request type path identity))
  ([type path request]
   (with-db-connection
     (str type collection "/" path)
     (fn [con]
       (request con)
       (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
         (slurp resp))))))

(defn db-post-xml
  [type path xml-request]
  (db-request
   type path
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
  (db-post-xml "rest" path
               (xml/sexp-as-element
                [::ex/query {:xmlns/ex "http://exist.sourceforge.net/NS/exist"}
                 [::ex/text [:-cdata xquery]]])))

(defn whoami []
  (query "" "xmldb:get-current-user()"))
