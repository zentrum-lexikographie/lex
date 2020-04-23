(ns zdl.lex.server.solr.query
  (:require [lucene-query.core :as lucene]
            [zdl.lex.server.solr.doc :refer [field-key->name]]))

(defn- translate-field-names [node]
  (if (vector? node)
    (let [[type args] node]
      (condp = type
        :field (let [[_ name] args]
                 [:field [:term (-> name keyword field-key->name)]])
        (vec (map translate-field-names node))))
    node))

(defn str->ast
  [s]
  (-> s lucene/str->ast translate-field-names))

(defn ast->str
  [ast]
  (-> ast translate-field-names lucene/ast->str))

(defn translate [s]
  (try
    (-> s lucene/str->ast translate-field-names lucene/ast->str)
    (catch Throwable t s)))

