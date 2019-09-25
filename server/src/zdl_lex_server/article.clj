(ns zdl-lex-server.article
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [tick.alpha.api :as t]
            [zdl-lex-common.util :refer [server-url]]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]
            [ring.util.request :as htreq]
            [ring.util.http-response :as htstatus])
  (:import [java.text Normalizer Normalizer$Form]))

(def xml-template (slurp (io/resource "template.xml") :encoding "UTF-8"))

(defn generate-id []
  (let [candidate #(str "E_" (rand-int 10000000))]
    (loop [id (candidate)]
      (if-not (solr/id-exists? id) id
              (recur (candidate))))))

(defn new-article-xml [xml-id form pos author]
  (let [doc (xml/->dom xml-template)
        element-by-name #(-> (.getElementsByTagName doc %) xml/->seq first)
        timestamp (t/format :iso-local-date (t/date))]
    (doto (element-by-name "Artikel")
      (.setAttribute "xml:id" xml-id)
      (.setAttribute "Zeitstempel" timestamp)
      (.setAttribute "Erstellungsdatum" timestamp)
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

(defn create-article [{:keys [zdl-lex-server.http/user
                              zdl-lex-server.http/password]
                       {:keys [form pos]} :params}]
  (let [xml-id (generate-id)
        xml (new-article-xml xml-id form pos user)
        filename (form->filename form)
        id (str new-article-collection "/" filename "-" xml-id ".xml")]
    (spit (store/id->file id) xml :encoding "UTF-8")
    (htstatus/ok {:id id :form form :pos pos})))

(defn get-article [{{:keys [path]} :path-params}]
  (store/with-read-lock 
    (let [f (store/id->file path)]
      (if (fs/exists? f)
        (htstatus/ok f)
        (htstatus/not-found path)))))

(defn post-article [{{:keys [path]} :path-params :as req}]
  (store/with-write-lock
    (let [f (store/id->file path)]
      (if (fs/exists? f)
        (do (spit f (htreq/body-string req) :encoding "UTF-8") (htstatus/ok f))
        (htstatus/not-found path)))))

(def ring-handlers
  ["/article"
   ["" {:put create-article}]
   ["/*path" {:get get-article :post post-article}]])
