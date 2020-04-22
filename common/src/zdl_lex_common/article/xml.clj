(ns zdl-lex-common.article.xml
  (:require [zdl-xml.util :as xml])
  (:import [net.sf.saxon.s9api XdmItem XdmNode XdmNodeKind]))

(def select-articles
  (comp seq (xml/selector "/d:DWDS/d:Artikel")))

(def select-forms
  (comp seq (xml/selector "./d:Formangabe")))

(def select-surface-forms
  (comp seq (xml/selector "./d:Formangabe/d:Schreibung")))

(def select-morphological-links
  (comp seq (xml/selector "./d:Verweise")))

(def select-senses
  (comp seq (xml/selector "./d:Lesart")))

(def select-refs
  (comp seq (xml/selector ".//d:Verweis")))

(defn doc->articles
  "Selects the DWDS article elements in a file."
  [doc]
  (->> doc xml/->xdm select-articles))

(declare xdm->str)

(defn element->str
  [^XdmNode element]
  (apply str (map xdm->str (.children element))))

(defn xdm->str
  [^XdmItem i]
  (if (.isAtomicValue i)
    (.getStringValue i)
    (let [^XdmNode node i]
      (condp = (.getNodeKind node)
        XdmNodeKind/TEXT (.getStringValue node)
        XdmNodeKind/ELEMENT
        (condp = (.. node (getNodeName) (getLocalName))
          "Streichung" ""
          "Loeschung" ""
          "Ziellesart" ""
          "Ziellemma" (or (.attribute node "Anzeigeform") (element->str node))
          (element->str node))
        ""))))

(defn xdm->text
  [node]
  (some-> node xml/->xdm xdm->str xml/text))

(defn texts
  [col]
  (some->> col seq (map xdm->text) (remove nil?) (distinct) (seq)))

