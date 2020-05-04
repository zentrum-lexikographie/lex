(ns zdl.lex.server.article
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [ring.util.http-response :as htstatus]
            [ring.util.request :as htreq]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.lock :as lock]
            [zdl.lex.server.solr.client :as solr-client]
            [zdl.lex.timestamp :as ts]
            [zdl.lex.fs :refer [file]]
            [zdl.xml.util :as xml])
  (:import [java.text Normalizer Normalizer$Form]
           java.io.File))

(def xml-template (slurp (io/resource "template.xml") :encoding "UTF-8"))

(defn generate-id []
  (let [candidate #(str "E_" (rand-int 10000000))]
    (loop [id (candidate)]
      (if-not (solr-client/id-exists? id) id
              (recur (candidate))))))

(defn new-article-xml [xml-id form pos author]
  (let [doc (xml/->dom xml-template)
        element-by-name #(-> (.getElementsByTagName doc %) xml/->seq first)
        timestamp (ts/format (java.time.LocalDate/now))]
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
      (str/replace " " "_")
      (str/replace #"[^\p{Alpha}\p{Digit}\-_]" "_")))

(def ^:private new-article-collection "Neuartikel")

(s/def ::resource string?)
(s/def ::token string?)
(s/def ::form string?)
(s/def ::pos string?)

(def existing-article-parameters
  {:path (s/keys :req-un [::resource])
   :query (s/keys :opt-un [::token])})
(def new-article-parameters
  {:query (s/keys :req-un [::form ::pos])})

(defn get-article [{{:keys [resource]} :path-params}]
  (let [^File f (file @git/dir resource)]
    (if (.isFile f)
      (htstatus/ok f)
      (htstatus/not-found resource))))

(defn create-article [{{:keys [user]} :identity
                       {:keys [form pos]} :params}]
  (locking @git/dir
    (let [xml-id (generate-id)
          xml (new-article-xml xml-id form pos user)
          filename (form->filename form)
          id (str new-article-collection "/" filename "-" xml-id ".xml")
          ^File f (file @git/dir id)
          ^File d (.getParentFile f)]
      (.mkdirs d)
      (spit f xml :encoding "UTF-8")
      (git/add! f)
      (git/publish-changes [f])
      (htstatus/ok {:id id :form form :pos pos}))))

(defn post-article [{{:keys [resource]} :path-params :as req}]
  (locking @git/dir
    (let [^File f (file @git/dir resource)]
      (if (.isFile f)
        (do
          (spit f (htreq/body-string req) :encoding "UTF-8")
          (git/publish-changes [f])
          (htstatus/ok f))
        (htstatus/not-found resource)))))

(def get-article-handler
  {:handler get-article
   :parameters existing-article-parameters})
(def ring-handlers
  ["/article/"
   [""
    {:put {:handler create-article
           :parameters new-article-parameters}}]
   ["*resource"
    {:get get-article-handler
     :head get-article-handler
     :post {:handler (lock/wrap-resource-lock post-article)
            :parameters existing-article-parameters}}]])
