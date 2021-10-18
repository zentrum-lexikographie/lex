(ns zdl.lex.server.solr.article
  (:require [clojure.core.async :as a]
            [clojure.data.xml :as dx]
            [zdl.lex.server.solr.client :as solr.client]
            [zdl.lex.server.solr.fields :as solr.fields]))

(defn update!
  [articles]
  (let [article-docs  (map solr.fields/article->doc articles)]
    (solr.client/update!
     (dx/sexp-as-element
      [:add {:commitWithin "1000"}
       (seq article-docs)]))))

(defn remove!
  [article-ids]
  (solr.client/update!
   (dx/sexp-as-element
    [:delete {:commitWithin "1000"}
     (for [id article-ids] [:id id])])))

(defn optimize!
  []
  (solr.client/update!
   (dx/sexp-as-element
    [:update
     [:commit]
     [:optimize]])))

(defn purge!
  [threshold]
  (a/go
    (a/<!
     (solr.client/update!
      (dx/sexp-as-element
       [:delete {:commitWithin "1000"}
        [:query (format "time_l:[* TO %s}" (.getTime threshold))]])))
    (a/<!
     (optimize!))))

(defn clear!
  []
  (a/go
    (a/<!
     (solr.client/update!
      (dx/sexp-as-element
       [:delete {:commitWithin "1000"}
        [:query "id:*"]])))
    (a/<!
     (optimize!))))
