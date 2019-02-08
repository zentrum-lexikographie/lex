(ns dwdsox.exist-db
  (:require [clojure.java.io :as io]
            [clojure.data.codec.base64 :as base64]
            [clojure.data.xml :as xml])
  (:import [java.io IOException]
           [java.net URL]))

(def url (or (System/getProperty "dwdsox.existdb.url")
             (System/getenv "DWDS_EXISTDB_URL")
             "http://spock.dwds.de:8080/exist/"))

(def user (or (System/getProperty "dwdsox.existdb.user")
              (System/getenv "DWDS_EXISTDB_USER")))

(def password (or (System/getProperty "dwdsox.existdb.password")
                  (System/getenv "DWDS_EXISTDB_PASSWORD")))

(defn- creds [user password]
  (apply str (map char (base64/encode (.getBytes (str user ":" password))))))

(xml/alias-uri 'exist "http://exist.sourceforge.net/NS/exist")

(defn query [q]
  (let [con (doto (-> (str url "rest/") (URL.) (.openConnection))
              (.setRequestMethod "POST")
              (.setRequestProperty "Content-Type" "application/xml")
              (.setDoOutput true)
              (.setDoInput true))
        xml-query (xml/sexp-as-element
                   [::exist/query {:xmlns/ex "http://exist.sourceforge.net/NS/exist"}
                    [::exist/text [:-cdata q]]
                    [::exist/properties
                     [::exist/property {:name "indent" :value "no"}]]])]

    (when (and user password)
      (.setRequestProperty con "Authorization" (str "Basic " (creds user password))))

    (try
      (with-open [req (io/writer (.getOutputStream con) :encoding "UTF-8")]
        (xml/emit xml-query req))
      (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
        (xml/parse resp))
      (catch IOException e
        (with-open [err (.getErrorStream con)]
          (slurp err :encoding "UTF-8"))))))

