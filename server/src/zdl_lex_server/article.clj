(ns zdl-lex-server.article
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [tick.alpha.api :as t]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]
            [zdl-lex-server.exist :as exist]
            [ring.util.http-response :as htstatus])
  (:import [java.text Normalizer Normalizer$Form]))

(def xml-template (slurp (io/resource "template.xml") :encoding "UTF-8"))

(defn generate-id []
  (let [candidate #(str "E_" (rand-int 10000000))]
    (loop [id (candidate)]
      (if-not (solr/id-exists? id) id
              (recur (candidate))))))

(defn new-article-xml [xml-id form pos author]
  (let [doc (xml/parse xml-template)
        element-by-name #(-> (.getElementsByTagName doc %) xml/nodes->seq first)]
    (doto (element-by-name "Artikel")
      (.setAttribute "xml:id" xml-id)
      (.setAttribute "Zeitstempel" (t/format :iso-local-date (t/date)))
      (.setAttribute "Autor" author))
    (.. (element-by-name "Schreibung") (setTextContent form))
    (.. (element-by-name "Wortklasse") (setTextContent pos))
    (xml/serialize doc)))

(defn form->filename [form]
  (-> form
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"\p{InCombiningDiacriticalMarks}" "")
      (str/replace "ß" "ss")
      (str/replace " " "-")
      (str/replace #"[^\p{Alpha}\p{Digit}\-]" "_")))

(def ^:private new-article-collection "Neuartikel")

(defn create-article [form pos user password]
  (let [xml-id (generate-id)
        xml (new-article-xml xml-id form pos user)
        filename (form->filename form)
        id (str new-article-collection "/" filename "-" xml-id ".xml")]
    (exist/create-article id xml user password)
    (spit (store/id->file id) xml :encoding "UTF-8")
    id))

(defn handle-creation [{:keys [zdl-lex-server.http/user
                               zdl-lex-server.http/password]
                        {:keys [form pos]} :params}]
  (htstatus/ok
   {:id (create-article form pos user password)}))
