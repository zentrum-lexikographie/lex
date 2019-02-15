(ns dwdsox.basex
  (:require [clojure.java.io :as io]
            [clojure.data.codec.base64 :as base64]
            [clojure.data.xml :as xml]
            [clojure.string :as string])
  (:import [java.io IOException]
           [java.net URL]))

(def url (or (System/getProperty "dwdsox.basex.url")
             (System/getenv "DWDS_BASEX_URL")
             "http://localhost:8984"))

(def user (or (System/getProperty "dwdsox.basex.user")
              (System/getenv "DWDS_BASEX_USER")
              "admin"))

(def password (or (System/getProperty "dwdsox.basex.password")
                  (System/getenv "DWDS_BASEX_PASSWORD")
                  "admin"))

(def collection (or (System/getProperty "dwdsox.basex.collection")
                    (System/getenv "DWDS_BASEX_COLLECTION")
                    "db/data"))

(def basic-creds
  (if (and user password)
    (apply str (map char (base64/encode (.getBytes (str user ":" password)))))))

(xml/alias-uri 'bx "http://basex.org/rest")

(defn- with-connection [p f]
  (let [con (-> (str url "/" p) (URL.) (.openConnection))]
    (try
      (when basic-creds
        (.setRequestProperty con "Authorization" (str "Basic " basic-creds)))
      (f con)
      (catch IOException e
        (with-open [err (io/reader (.getErrorStream con) :encoding "UTF-8")]
          (throw (ex-info "I/O error while talking to XML database"
                          {:http-status (.getResponseCode con)
                           :http-message (.getResponseMessage con)
                           :http-body (slurp err)})))))))

(defn resource [p]
  (with-connection
    (str "rest/" collection "/" p)
    (fn [con]
      (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
        (slurp resp)))))

(defn query [q]
  (with-connection
    (str "rest/" collection)
    (fn [con]
      (doto con
        (.setRequestMethod "POST")
        (.setRequestProperty "Content-Type" "application/xml")
        (.setDoOutput true)
        (.setDoInput true))
      (with-open [req (io/writer (.getOutputStream con) :encoding "UTF-8")]
        (xml/emit
         (xml/sexp-as-element
          [::bx/query {:xmlns/bx "http://basex.org/rest"}
           [::bx/text [:-cdata q]]])
         req))
      (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
        (slurp resp)))))
