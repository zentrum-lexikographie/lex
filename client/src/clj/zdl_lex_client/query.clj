(ns zdl-lex-client.query
  (:require [lucene-query.core :as lucene]
            [clojure.string :as str]))

(def ^:private field-name-mapping
  {"autor" "authors"
   "red" "editors"
   "def" "definitions"
   "form" "forms"
   "datum" "timestamp"
   "klasse" "pos"
   "bedeutung" "senses"
   "quelle" "sources"
   "status" "status"
   "tranche" "tranche"
   "typ" "type"
   "volltext" "text"})

(defn- expand-date [v]
  (-> v
      (str/replace #"^(\d{4}-\d{2})$" "$1-01")
      (str/replace #"^(\d{4}-\d{2}-\d{2})$" "$1T00\\:00\\:00Z")))

(defn translate-node [node]
  (if (vector? node)
    (let [[type arg] node]
      (condp = type
        :field (let [[_ name] arg]
                 [:field [:term (or (field-name-mapping name) name)]])
        :term [:term (expand-date arg)]
        (vec (map translate-node node))))
    node))

(def str->ast (comp translate-node lucene/str->ast))

(def ast->str (comp lucene/ast->str translate-node))

(def translate (comp lucene/ast->str translate-node lucene/str->ast))

(defn valid? [q]
  (try (translate q) true (catch Throwable t false)))

(comment
  (str->ast "autor:(a OR b) AND typ:c AND quelle:d")
  (str->ast "datum:[1999-01 TO 2018-01-01}")
  (str->ast "def:test AND klasse:Verb")
  (valid? "def:test klasse:Verb"))



