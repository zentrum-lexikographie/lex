(ns zdl.lex.server.solr.doc
  (:require [clojure.string :as str]))

(defn field-name->key
  "Translates a Solr field name into a keyword.

   Strips datatype-specific suffixes and replaces underscores."
  [n]
  (condp = n
    "_text_" :text
    (-> n
        (str/replace #"_((dts)|(dt)|(s)|(ss)|(t)|(i)|(l))$" "")
        (str/replace "_" "-")
        keyword)))

(defn- field-name-suffix
  "Suffix for a given field (keyword), expressing its datatype."
  [k]
  (condp = k
    :id ""
    :language ""
    :xml-descendent-path ""
    :weight "_i"
    :time "_l"
    :definitions "_t"
    :last-modified "_dt"
    :timestamp "_dt"
    :timestamps "_dts"
    :author "_s"
    :editor "_s"
    :form "_s"
    :source "_s"
    "_ss"))

(defn field-key->name
  "Translates a keyword into a Solr field name."
  [k]
  (condp = k
    :text "_text_"
    (let [field-name (str/replace (name k) "-" "_")
          field-suffix (field-name-suffix k)]
      (str field-name field-suffix))))

(def ^:private abstract-fields
  "Solr fields which comprise the document abstract/summary."
  [:id :type :status :provenance
   :last-modified :timestamp
   :author :authors :editors :editor
   :sources :source
   :form :forms :pos :definitions
   :errors])

(defn- basic-field
  "Mapping of basic Solr fields."
  [[k v]]
  (if-not (nil? v)
    [(field-key->name k) (if (coll? v) (vec v) [(str v)])]))

(defn- attr-field
  "Mapping of Solr fields, differentiated by the type."
  [prefix suffix attrs]
  (let [all-values (->> attrs vals (apply concat) (seq))]
    (-> (for [[type values] attrs]
          (let [type (-> type name str/lower-case)
                field (str prefix "_" type "_" suffix)]
            [field values]))
        (conj (if all-values
                [(str prefix "_" suffix) all-values])))))

(defn article->fields
  "Returns Solr fields/values for a given article."
  [{:keys [id] :as article}]
  (let [abstract (select-keys article abstract-fields)
        preamble {:id id
                  :language "de"
                  :time (str (System/currentTimeMillis))
                  :xml-descendent-path id
                  :abstract (pr-str abstract)}
        main-fields (dissoc article
                            :id :file
                            :timestamps :authors :editors :sources
                            :references :ref-ids :senses)
        fields (->> [(map basic-field preamble)
                     (map basic-field main-fields)
                     (attr-field "timestamps" "dts" (article :timestamps))
                     (attr-field "authors" "ss" (article :authors))
                     (attr-field "editors" "ss" (article :editors))
                     (attr-field "sources" "ss" (article :sources))]
                    (mapcat identity)
                    (remove nil?)
                    (into {}))]
    (for [[name values] (sort fields) value (sort values)]
      [name value])))

(defn article->doc
  [article]
  [:doc
   (for [[k v] (article->fields article)]
     [:field {:name k} v])])
