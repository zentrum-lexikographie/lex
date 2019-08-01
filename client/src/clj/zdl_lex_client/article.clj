(ns zdl-lex-client.article
  (:require [cemerick.url :refer [url]]
            [clojure.string :as str]
            [zdl-lex-client.env :refer [config]]))

(defn status->color [status]
  (condp = (str/trim status)
    "Artikelrumpf" "#ffcccc"
    "Lex-zur_Abgabe" "#98fb98" ; "#ffff00"
    "Red-1" "#ffec8b"
    "Red-f" "#aeecff" ; "#ccffcc"
    "#ffffff"))

(def ^:private base (config :webdav-base))

(defn id->url [id] (->> id (url base) str))

(defn url->id [u]
  (if (str/starts-with? u base)
    (subs u (inc (count base)))))

(comment
  (id->url "test.xml")
  (url->id "http://spock.dwds.de:8080/exist/webdav/db/dwdswb/data/test.xml"))
