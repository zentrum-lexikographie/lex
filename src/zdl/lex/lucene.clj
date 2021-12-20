(ns zdl.lex.lucene
  "Handling of Apache Lucene (Solr) Standard Query syntax"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [instaparse.core :as insta :refer [defparser]]))

;; ## Escaping of special chars in regexps, terms etc.

(defn escape
  [char-re s]
  (str/replace s char-re #(str "\\" %)))

(defn unescape
  [char-re]
  (let [re (re-pattern (str "\\\\" char-re))]
    (fn [s] (str/replace s re #(subs % 1 2)))))

(def escape-quoted
  (partial escape #"\""))

(def unescape-quoted
  (unescape "\""))

(def escape-regexp
  (partial escape #"/"))
(def unescape-regexp
  (unescape "/"))

(def escape-pattern
  (partial escape #"[\!\(\)\:\^\[\]\"\{\}\~\\]"))

(def unescape-pattern
  (unescape "[\\!\\(\\)\\:\\^\\[\\]\\\"\\{\\}\\~\\\\]"))

(def escape-term
  (comp (partial escape #"[\*\?]") escape-pattern))
(def unescape-term
  (comp unescape-pattern (unescape "[\\*\\?]")))

;; ## App-specifics

(defn field-name->key
  "Translates a Solr field name into a keyword.

   Strips datatype-specific suffixes and replaces underscores."
  [n]
  (if (= n "_text_") :text
      (-> n
          (str/replace #"_((dts)|(dt)|(s)|(ss)|(t)|(i)|(l))$" "")
          (str/replace "_" "-")
          keyword)))

(defn- field-name-suffix
  "Suffix for a given field (keyword), expressing its datatype."
  [k]
  (condp = k
    :id                  ""
    :language            ""
    :doc-type            ""
    :xml-descendent-path ""
    :weight              "_i"
    :time                "_l"
    :definitions         "_t"
    :last-modified       "_dt"
    :timestamp           "_dt"
    :author              "_s"
    :editor              "_s"
    :form                "_s"
    :source              "_s"
    :type                "_s"
    :provenance          "_s"
    :last-updated        "_s"
    :summary             "_s"
    :category            "_s"
    :status              "_s"
    :severity            "_s"
    :reporter            "_s"
    :handler             "_s"
    :resolution          "_s"
    :attachments         "_i"
    :notes               "_i"
    "_ss"))

(defn field-key->name
  "Translates a keyword into a Solr field name."
  [k]
  (condp = k
    :text "_text_"
    (let [field-name   (str/replace (name k) "-" "_")
          field-suffix (field-name-suffix k)]
      (str field-name field-suffix))))

(def field-aliases
  "Aliasing of index field names, mostly translations."
  {"autor" "author"
   "red" "editor"
   "def" "definitions"
   "fehler" "errors"
   "form" "forms"
   "datum" "timestamp"
   "klasse" "pos"
   "bedeutung" "senses"
   "quelle" "source"
   "status" "status"
   "tranche" "tranche"
   "typ" "type"
   "ersterfassung" "provenance"
   "volltext" "text"})

(defn translate-fields
  "Applies field aliases and data type suffixes during AST transformation."
  [[type v :as node]]
  [:field (if (= type :term)
            [:term (-> (get field-aliases v v) field-name->key field-key->name)]
            node)])

(defn expand-date-literals
  "Date literals can be specified as month or day prefixes, i.e. `2020-10` or
  `2020-11-01`."
  [[_ v]]
  [:term
   (-> v
       (str/replace #"^(\d{4}-\d{2})$" "$1-01")
       (str/replace #"^(\d{4}-\d{2}-\d{2})$" "$1T00\\:00\\:00Z"))])

;; ## Parsing queries into ASTs

(defparser parse-str
  (io/resource "zdl/lex/lucene-query.bnf"))

(defn- str-node
  ([type]
   (str-node type identity))
  ([type unescape-fn]
   (fn [& args] [type (->> args (apply str) unescape-fn)])))

(def ast->ast
  (->>
   {:field translate-fields
    :term (comp expand-date-literals (str-node :term unescape-term))
    :pattern (str-node :pattern unescape-pattern)
    :regexp (str-node :regexp unescape-regexp)
    :quoted (str-node :quoted unescape-quoted)
    :fuzzy (str-node :fuzzy)
    :boost (str-node :boost)}
   (partial insta/transform)))

(defn error->ex
  [r]
  (if (vector? r) r (throw (ex-info "Cannot parse Lucene/Solr query" r))))

(def str->ast
  (comp error->ex ast->ast parse-str))

;; ## Serializing ASTs

(defn ast->str
  [[type & [arg :as args]]]
  (condp = type
    :sub-query (str "(" (apply str (map ast->str args)) ")")
    :range (let [[lp lo up rp] args]
             (str lp (ast->str lo) " TO " (ast->str up) rp))

    :field (str (apply str (map ast->str args)) ":")
    :boost (str "^" arg)
    :fuzzy (str "~" arg)

    :and " AND "
    :or " OR "
    :not "!"
    :must "+"
    :must-not "-"
    :all "*"

    :term (escape-term arg)
    :pattern (escape-pattern arg)
    :quoted (str "\"" (escape-quoted arg) "\"")
    :regexp (str "/" (escape-regexp arg) "/")

    (apply str (map ast->str args))))

(def translate
  "String-to-string conversion, applying AST transformations."
  (comp ast->str str->ast))

(comment
  (str->ast "quelle:a/b"))

(defn valid?
  "A valid query can be parsed/transformed"
  [q]
  (try (translate q) true (catch Throwable _ false)))
