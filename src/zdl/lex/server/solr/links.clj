(ns zdl.lex.server.solr.links
  (:require [zdl.lex.server.solr.client :as solr.client]
            [clojure.core.async :as a]
            [zdl.lex.server.solr.fields :as solr.fields]
            [zdl.lex.lucene :as lucene]))

(defn str->set
  [v]
  (some->> (if (string? v) [v] v) (into (sorted-set))))

(defn value->clause
  [v]
  [:clause [:value [:quoted v]]])

(defn set->field-query
  [field vs]
  [:clause
   [:field [:term field]]
   [:sub-query (into [:query] (interpose [:or] (map value->clause vs)))]])

(defn request->lucene-query
  [{:keys [anchors links]}]
  (let [anchors (str->set anchors)
        links   (str->set links)]
    (when (or anchors links)
        (into
         [:query]
         (interpose
          [:or]
          (cond-> []
            anchors (conj (set->field-query "anchors_ss" anchors))
            links   (conj (set->field-query "links_ss"links))))))))

(defn request->query
  [{{:keys [query]} :parameters}]
  (when-let [q (request->lucene-query query)]
    {"q"    (lucene/ast->str q)
     "fq"   "doc_type:article"
     "fl"   "abstract_ss,anchors_ss,links_ss"
     "rows" "1000"}))

(defn parse-response
  [{{{:keys [numFound docs]} :response} :body}]
  {:total  numFound
   :result (for [{:keys [anchors_ss links_ss] :as doc} docs]
             (assoc (solr.fields/doc->abstract doc)
                    :anchors anchors_ss
                    :links links_ss))})
;; # Query

(defn handle-query
  [req]
  (a/go
    (if-let [query (request->query req)]
      (if-let [response (a/<! (solr.client/query query))]
        {:status 200
         :body   (parse-response response)}
        {:status 502})
      {:status 404})))
