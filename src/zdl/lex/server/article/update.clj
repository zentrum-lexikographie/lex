(ns zdl.lex.server.article.update
  (:require [clojure.core.async :as a]
            [clojure.data.xml :as dx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.walk :refer [postwalk]]
            [zdl.lex.article.xml :as axml]
            [zdl.lex.bus :as bus]
            [zdl.lex.fs :refer [file file?]]
            [zdl.lex.lucene :as lucene]
            [zdl.lex.server.auth :as auth]
            [zdl.lex.server.article :as server.article]
            [zdl.lex.server.git :as server.git]
            [zdl.lex.server.solr.client :as solr.client]
            [zdl.lex.timestamp :as ts])
  (:import java.io.File
           [java.text Normalizer Normalizer$Form]))

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

(def ^:private new-article-collection "Neuartikel")

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

(defn handle-create
  [{{:keys [user]} ::auth/identity {{:keys [form pos]} :query} :parameters}]
  (a/go
    (server.git/lock!)
    (try
      (when-let [xml-id (a/<! (generate-id))]
        (let [xml      (new-article-xml xml-id form pos user)
              filename (form->filename form)
              id       (str new-article-collection "/" filename "-" xml-id ".xml")
              f        (file server.git/dir id)]
          (doto (.getParentFile f) (.mkdirs))
          (spit f xml :encoding "UTF-8")
          (server.git/add! f :lock? false)
          (server.article/update! [f])
          {:status 200
           :body   {:id   id
                    :form form
                    :pos  pos}}))
      (finally
        (server.git/unlock!)))))

(defn handle-read
  [{{{:keys [resource]} :path} :parameters}]
  (let [^File f (file server.git/dir resource)]
    (if (file? f)
      {:status 200 :body f}
      {:status 404 :body resource})))

(defn handle-write
  [{{{:keys [resource]} :path} :parameters :keys [body]}]
  (server.git/with-lock
    (let [^File f (file server.git/dir resource)]
      (if (file? f)
        (do
          (with-open [os (io/output-stream f)]
            (io/copy body os))
          (server.article/update! [f])
          {:status 200 :body f})
        {:status 404 :body resource}))))

(comment
  (handle-create {::auth/identity {:user "middell"}
                  :parameters     {:query {:form "Test"
                                           :pos  "Substantiv"}}}))
