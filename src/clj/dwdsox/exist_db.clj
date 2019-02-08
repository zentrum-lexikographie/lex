(ns dwdsox.exist-db
  (:require [clojure.java.io :as io]
            [clojure.data.codec.base64 :as base64]
            [clojure.data.xml :as xml]
            [clojure.string :as string])
  (:import [java.io IOException]
           [java.net URL]))

(def url (or (System/getProperty "dwdsox.existdb.url")
             (System/getenv "DWDS_EXISTDB_URL")
             "http://spock.dwds.de:8080/exist"))

(def user (or (System/getProperty "dwdsox.existdb.user")
              (System/getenv "DWDS_EXISTDB_USER")))

(def password (or (System/getProperty "dwdsox.existdb.password")
                  (System/getenv "DWDS_EXISTDB_PASSWORD")))

(def collection (or (System/getProperty "dwdsox.existdb.collection")
                    (System/getenv "DWDS_EXISTDB_COLLECTION")
                    "/db/dwdswb/data"))

(def basic-creds
  (if (and user password)
    (apply str (map char (base64/encode (.getBytes (str user ":" password)))))))

(xml/alias-uri 'ex "http://exist.sourceforge.net/NS/exist")

(defn- with-connection [p f]
  (let [con (-> (str url "/" p) (URL.) (.openConnection))]
    (try
      (when basic-creds
        (.setRequestProperty con "Authorization" (str "Basic " basic-creds)))
      (f con)
      (catch IOException e
        (with-open [err (io/reader (.getErrorStream con) :encoding "UTF-8")]
          (throw (Exception. (slurp err))))))))

(defn resource [p]
  (with-connection
    (str "webdav/" collection "/" p)
    (fn [con]
      (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
        (slurp resp)))))

(defn query [q]
  (with-connection
    "rest/"
    (fn [con]
      (doto con
        (.setRequestMethod "POST")
        (.setRequestProperty "Content-Type" "application/xml")
        (.setDoOutput true)
        (.setDoInput true))
      (with-open [req (io/writer (.getOutputStream con) :encoding "UTF-8")]
        (xml/emit
         (xml/sexp-as-element
          [::ex/query {:xmlns/ex "http://exist.sourceforge.net/NS/exist"}
           [::ex/text [:-cdata q]]
           [::ex/properties
            [::ex/property {:name "indent" :value "no"}]]])
         req))
      (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
        (slurp resp)))))
