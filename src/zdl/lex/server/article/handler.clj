(ns zdl.lex.server.article.handler
  (:require
   [clojure.core.async :as a]
   [gremid.data.xml :as dx]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.walk :refer [postwalk]]
   [slingshot.slingshot :refer [try+]]
   [zdl.lex.article.xml :as axml]
   [zdl.lex.lucene :as lucene]
   [zdl.lex.server.article.lock :as article.lock]
   [zdl.lex.server.auth :as auth]
   [zdl.lex.server.git :as server.git]
   [zdl.lex.server.solr.client :as solr.client]
   [zdl.lex.timestamp :as ts])
  (:import
   (java.io File)
   (java.text Normalizer Normalizer$Form)))

(def xml-template
  (axml/read-xml (io/resource "template.xml")))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")
(dx/alias-uri :xxml "http://www.w3.org/XML/1998/namespace")

(def article-namespaces
  {:xmlns "http://www.dwds.de/ns/1.0"})

(defn new-article-xml
  [xml-id form pos author]
  (dx/emit-str
   (postwalk
    (fn [node]
      (if (map? node)
        (condp = (node :tag)
          ::dwds/DWDS       (update node :attrs merge article-namespaces)
          ::dwds/Artikel    (let [ts (ts/format (java.time.LocalDate/now))]
                              (update node :attrs merge
                                      {::xxml/id         xml-id
                                       :Zeitstempel      ts
                                       :Erstellungsdatum ts
                                       :Autor            author}))
          ::dwds/Schreibung (assoc node :content (list form))
          ::dwds/Wortklasse (assoc node :content (list pos))
          node)
        node))
    xml-template)))

(defn form->filename [form]
  (-> form
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"\p{InCombiningDiacriticalMarks}" "")
      (str/replace "ß" "ss")
      (str/replace " " "_")
      (str/replace #"[^\p{Alpha}\p{Digit}\-_]" "_")))

(def ^:private new-article-collection "Neuartikel-003")

(defn generate-id
  []
  (a/go-loop [n 0]
    (let [id       (str "E_" (rand-int 10000000))
          id-query [:query
                    [:clause
                     [:field [:term "id"]]
                     [:value [:pattern (str "*" id "*")]]]]
          request  {:q (lucene/ast->str id-query) :rows 0}]
      (when-let [response (a/<! (solr.client/query request))]
        (let [num-found (get-in response [:body :response :numFound] 1)]
          (cond
            (= 0 num-found) id
            (= 10 n)        (log/error (str "Maximum number of article id "
                                            "generations exceeded"))
            :else           (recur (inc n))))))))

(defn handle-read
  [{{{:keys [resource]} :path} :parameters}]
  (if-let [^File f (server.git/get-file resource)]
    {:status 200 :body f}
    {:status 404 :body resource}))

(defn create-xml!
  [xml]
  (fn [f] (spit f xml :encoding "UTF-8")))

(defn handle-create
  [{{:keys [user]} ::auth/identity {{:keys [form pos]} :query} :parameters}]
  (when-let [xml-id (a/<!! (generate-id))]
    (let [filename (form->filename form)
          path     (str new-article-collection "/" filename "-" xml-id ".xml")
          xml      (new-article-xml xml-id form pos user)]
      {:status  200
       :headers {"X-Lex-ID" path}
       :body    (server.git/edit! path (create-xml! xml))})))

(defn write-xml!
  [lock body]
  (article.lock/editor lock (fn [f] (io/copy body f))))

(defn handle-write
  [{:keys [body] :as req}]
  (let [{:keys [resource] :as lock} (article.lock/request->lock req)]
    (if-not (server.git/get-file resource)
      {:status 404
       :body   resource}
      (try+
       {:status 200
        :body   (server.git/edit! resource (write-xml! lock body))}
       (catch [:type ::article.lock/locked] {:keys [lock]}
         {:status 423
          :body   lock})))))

(comment
  (handle-create {::auth/identity {:user "middell"}
                  :parameters     {:query {:form "Test"
                                           :pos  "Substantiv"}}}))
