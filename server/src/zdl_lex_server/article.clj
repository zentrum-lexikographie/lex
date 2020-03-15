(ns zdl-lex-server.article
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [ring.util.http-response :as htstatus]
            [ring.util.request :as htreq]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.timestamp :as ts]
            [zdl-xml.util :as xml]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.lock :as lock]
            [zdl-lex-server.solr.client :as solr-client]
            [clojure.spec.alpha :as s])
  (:import [java.text Normalizer Normalizer$Form]))

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
  (let [id->file (article/id->file git/dir)
        f (id->file resource)]
    (if (fs/exists? f)
      (htstatus/ok f)
      (htstatus/not-found resource))))

(defn create-article [{{:keys [user]} :identity
                       {:keys [form pos]} :params}]
  (locking git/dir
    (let [xml-id (generate-id)
          xml (new-article-xml xml-id form pos user)
          filename (form->filename form)
          id (str new-article-collection "/" filename "-" xml-id ".xml")
          id->file (article/id->file git/dir)
          f (id->file id)]
      (spit f xml :encoding "UTF-8")
      (git/git-add f)
      (git/publish-changes [f])
      (htstatus/ok {:id id :form form :pos pos}))))

(defn post-article [{{:keys [resource]} :path-params :as req}]
  (locking git/dir
    (let [id->file (article/id->file git/dir)
          f (id->file resource)]
      (if (fs/exists? f)
        (do
          (spit f (htreq/body-string req) :encoding "UTF-8")
          (git/publish-changes [f])
          (htstatus/ok f))
        (htstatus/not-found resource)))))

(def ring-handlers
  ["/article/"
   [""
    {:put {:handler create-article
           :parameters new-article-parameters}}]
   ["*resource"
    {:get {:handler get-article
           :parameters existing-article-parameters}
     :post {:handler (lock/wrap-resource-lock post-article)
            :parameters existing-article-parameters}}]])
