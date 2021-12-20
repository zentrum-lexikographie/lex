(ns zdl.lex.server.solr.fields
  (:require [clojure.string :as str]
            [zdl.lex.lucene :as lucene]))

(def article-abstract-fields
  "Solr fields which comprise the document abstract/summary."
  [:id :type :status :provenance
   :last-modified :timestamp
   :author :editor :source
   :form :forms :pos :definitions
   :errors])

(def article-basic-fields
  [:type :status :source
   :author :editor
   :timestamp :last-modified
   :tranche :provenance
   :form :forms :pos :gender
   :definitions
   :errors])

(defn attr-fields
  "Mapping of Solr fields, differentiated by the type."
  [prefix suffix attrs]
  (->>
   (conj
    (for [[type values] attrs]
      (let [type (str/lower-case (name type))]
        [(str prefix "_" type "_" suffix) values]))
    [(str prefix "_" suffix) (seq (flatten (vals attrs)))])
   (into {})))

(defn article->fields
  "Returns Solr fields/values for a given article."
  [{:keys [id] :as article}]
  (merge
   {:id                  id
    :language            "de"
    :doc-type            "article"
    :time                (str (System/currentTimeMillis))
    :xml-descendent-path id
    :abstract            (pr-str (select-keys article article-abstract-fields))
    :anchors             (seq (article :anchors))
    :links               (seq (map :anchor (article :links)))}
   (select-keys article article-basic-fields)
   #_(attr-fields "timestamps" "dts" (article :timestamps))
   #_(attr-fields "authors" "ss" (article :authors))
   #_(attr-fields "editors" "ss" (article :editors))
   #_(attr-fields "sources" "ss" (article :sources))))

(def issue-abstract-fields
  [:id :form :last-updated :summary :status :category :severity :resolution])

(def issue-basic-fields
  [:form :last-updated :summary :category :status :severity
   :reporter :handler :resolution :attachments :notes])

(defn issue->fields
  [issue]
  (merge
   {:id       (issue :id)
    :language "de"
    :doc-type "issue"
    :time     (str (System/currentTimeMillis))
    :abstract (pr-str (select-keys issue issue-abstract-fields))}
   (select-keys issue issue-basic-fields)))

(defn fields->doc
  [fields]
  [:doc
   (for [[k vs] (sort fields)
         :when  (some? vs)
         v      (if (coll? vs) (sort vs) [vs])
         :when  (some? v)]
     [:field {:name (lucene/field-key->name k)} v])])

(def article->doc
  (comp fields->doc article->fields))

(def issue->doc
  (comp fields->doc issue->fields))

(defn doc->abstract
  [{:keys [abstract_ss]}]
  (read-string (first abstract_ss)))
