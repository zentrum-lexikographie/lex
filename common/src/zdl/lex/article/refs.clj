(ns zdl.lex.article.refs
  (:require [clojure.string :as str]
            [zdl.lex.article.xml :as axml]
            [zdl.lex.util :refer [->clean-map]]
            [zdl.xml.util :as xml]))

(def select-hidx
  (comp first seq (xml/selector "@hidx/string()")))

(defn hidx
  [node]
  (-> node xml/->xdm select-hidx axml/xdm->text))

(defn id
  [node]
  (some->> [(axml/xdm->text node) (hidx node)]
           (remove nil?) seq (str/join \#)))

(def select-lexeme-ref
  (comp seq (xml/selector "./d:Ziellemma")))

(def select-sense-ref
  (comp seq (xml/selector "./d:Ziellesart")))

(defn link
  [node]
  (let [node (xml/->xdm node)
        lexeme (->> node select-lexeme-ref axml/texts first)
        sense (->> node select-sense-ref axml/texts first)]
    (some->> [lexeme sense] (remove nil?) seq vector)))

(defn links
  "Extracts all references from an article"
  [article]
  (let [links (fn [ctxs] (->> (mapcat axml/select-refs ctxs)
                              (map link) (remove nil?) (distinct)))]
    (->clean-map
     {:form (links (axml/select-forms article))
      :morphology (links (axml/select-morphological-links article))
      :sense (links (axml/select-senses article))})))
