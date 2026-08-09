(ns zdl.lex.corpora.wikipedia
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [ont-app.igraph-jena.core :as jgraph]
   [ont-app.igraph.core :as igraph]
   [ont-app.vocabulary.core :as voc]
   [zdl.lex.corpora.http :as http])
  (:import
   (org.apache.jena.riot Lang RDFDataMgr)))

(voc/put-ns-meta!
 'de.wikipedia.category
 {:vann/preferredNamespacePrefix "dewkpcat"
  :vann/preferredNamespaceUri    "https://de.wikipedia.org/wiki/Kategorie:"})

(voc/put-ns-meta!
 'org.mediawiki.ontology
 {:vann/preferredNamespacePrefix "mwont"
  :vann/preferredNamespaceUri    "https://www.mediawiki.org/ontology#"})

(def categories-dump
  "https://dumps.wikimedia.org/other/categoriesrdf/20260620/dewiki-20260620-categories.ttl.gz")

(defonce categories
  (with-open [is (-> {:method :get :url categories-dump :as :stream}
                     (http/request) :body java.util.zip.GZIPInputStream.)]
    (doto (jgraph/make-jena-graph) (-> :model (RDFDataMgr/read is Lang/TTL)))))

(defn category-desc
  [[category pages]]
  (let [category-name (-> (name category)
                          (java.net.URLDecoder/decode)
                          (str/replace #"_" " "))]
    (format "%s (%,d)" category-name pages)))

(defn subcategories
  [category]
  (->> (format "select ?category ?pages
                where { ?category mwont:isInCategory <%s> .
                        ?category mwont:pages ?pages }"
               (voc/as-uri-string category))
       (voc/prepend-prefix-declarations)
       (igraph/query categories)
       (map (juxt :category :pages))
       (sort-by (comp - second))))

(defn subject-desc
  [[category _pages :as subject]]
  (let [subcategories (remove (some-fn (comp zero? second)
                                       (comp #(str/includes? % "Liste") first))
                              (subcategories category))
        subcategories (sort-by (comp - second) subcategories)]
    (into [(category-desc subject)] (map category-desc) subcategories)))

(comment
  (with-open [w (io/writer (io/file "wikipedia-sachklassifikation.csv"))]
    (csv/write-csv w (map subject-desc (subcategories :dewkpcat/Sachsystematik)))))
