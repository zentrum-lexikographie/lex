(ns zdl-lex-server.article
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]))

(defn xml [article]
  (let [doc-loc (-> article io/input-stream
                    (xml/parse :namespace-aware false) zip/xml-zip)
        article-loc (zx/xml1-> doc-loc :DWDS :Artikel)]
    (or article-loc (throw (ex-info (str article) (zip/node doc-loc))))))

(defn- normalize-space [s] (str/trim (str/replace s #"\s+" " ")))

(defn- text [loc]
  (some-> loc zx/text normalize-space))

(defn- texts [& args]
  (->> (apply zx/xml-> args) (map text) (remove empty?)
       (into #{}) (vec) (sort) (seq)))

(defn- attrs [article-loc attr]
  (let [attr-locs (zx/xml-> article-loc dz/descendants
                                 #(string? (zx/attr % attr)))
        attr-nodes (map zip/node attr-locs)
        typed-attrs (map #(hash-map (:tag %) [(get-in % [:attrs attr])])
                         attr-nodes)]
    (if (empty? typed-attrs) {} (apply merge-with concat typed-attrs))))

(defn excerpt [article-loc]
  (let [forms (texts article-loc :Formangabe :Schreibung)
        pos (texts article-loc :Formangabe :Grammatik :Wortklasse)
        definitions (texts article-loc dz/descendants :Definition)
        senses (texts article-loc dz/descendants :Bedeutungsebene)
        usage-period (texts article-loc dz/descendants :Gebrauchszeitraum)
        area (texts article-loc dz/descendants :Sprachraum)
        styles (texts article-loc dz/descendants :Stilebene)
        colouring (texts article-loc dz/descendants :Stilfaerbung)
        morphological-rels (texts article-loc :Verweise :Verweis :Ziellemma)
        sense-rels (texts article-loc dz/descendants
                          :Lesart :Verweise :Verweis :Ziellemma)
        {:keys [Typ Tranche Status]} (-> article-loc zip/node :attrs)
        timestamps (attrs article-loc :Zeitstempel)
        authors (attrs article-loc :Autor)
        sources (attrs article-loc :Quelle)
        excerpt {:forms forms
                 :pos pos
                 :definitions definitions
                 :senses senses
                 :usage-period usage-period
                 :styles styles
                 :colouring colouring
                 :area area
                 :morphological-rels morphological-rels
                 :sense-rels sense-rels
                 :timestamps timestamps
                 :authors authors
                 :sources sources
                 :type Typ
                 :tranche Tranche
                 :status Status}]
    (apply dissoc excerpt (for [[k v] excerpt :when (nil? v)] k))))

(def abstract-fields [:forms :pos :definitions :type :status :authors])
