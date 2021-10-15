(ns zdl.xml.rngom
  "RELAX NG schema parser, based on Kohsuke Kawaguchi's RNG-OM."
  (:require [zdl.xml.util :as util])
  (:import javax.xml.namespace.QName
           org.kohsuke.rngom.ast.util.CheckingSchemaBuilder
           [org.kohsuke.rngom.digested DAttributePattern DElementPattern
            DPattern DPatternWalker DRefPattern DSchemaBuilderImpl
            DValuePattern DXmlTokenPattern]
           org.kohsuke.rngom.parse.xml.SAXParseable
           org.xml.sax.helpers.DefaultHandler))

;; ## Pattern predicates

(def xml-token-pattern?
  (partial instance? DXmlTokenPattern))

(def element-pattern?
  (partial instance? DElementPattern))

(def attribute-pattern?
  (partial instance? DAttributePattern))

(def value-pattern?
  (partial instance? DValuePattern))

;; ## Parsing and schema traversal

(defn parse-schema
  "Parses a RELAX NG schema in XML syntax."
  [src]
  (let [eh (proxy [DefaultHandler] [] (error [e] (throw e)))
        schema-builder (CheckingSchemaBuilder. (DSchemaBuilderImpl.) eh)
        schema (util/->input-source src)
        schema (SAXParseable. schema eh)]
    (.. schema (parse schema-builder))))

(defn traverse
  "Traverse the schema graph, starting at a given pattern, following
  references and returning a vector of traversed patterns.

  With a given predicate `descend?`, traversal stops at XML tokens not
  fulfilling the predicate."
  ([^DPattern start]
   (traverse start (constantly true)))
  ([^DPattern start descend?]
   (let [patterns (transient [])
         add! (fn [p] (conj! patterns p) nil)
         seen-refs (transient #{})]
     (->>
      (proxy [DPatternWalker] []
        (onXmlToken [p] (add! p) (when (descend? p) (.onUnary this p)))
        (onData [p] (add! p))
        (onEmpty [p] (add! p))
        (onText [p] (add! p))
        (onValue [p] (add! p))
        (onNotAllowed
          [_]
          (throw (IllegalStateException. "<notAllowed/> not supported")))
        (onRef
          [^DRefPattern rp]
          (let [name (.getName rp)]
            (when-not (seen-refs name)
              (conj! seen-refs name)
              (.. rp (getTarget) (getPattern) (accept this))))))
      (.accept start))
     (persistent! patterns))))

(defn traverse-children
  "Traverses the schema, starting at a given pattern and curtailing the graph by
  stopping traversal on descendant XML tokens.

  This way, only child entities (elements, attributes, data, text and value
  pattern) are returned."
  [^DPattern start]
  (traverse start (some-fn #{start} (complement xml-token-pattern?))))

(defn parse-names
  "Parses name class of a given XML Token, returning the set of referenced
  qualified names.

  (Does not handle exception classes.)"
  [^DXmlTokenPattern token-pattern]
  (let [names (transient #{})
        name-class (.getName token-pattern)
        throw-illegal! (fn [] (throw (IllegalArgumentException. name-class)))]
    (->>
     (proxy [org.kohsuke.rngom.nc.NameClassWalker] []
       (visitName [^QName qn] (conj! names qn) nil)
       (visitAnyName [])
       (visitNsName [ns] (throw-illegal!))
       (visitAnyNameExcept [] (throw-illegal!))
       (visitNsNameExcept [ns nc] (throw-illegal!))
       (visitNull [] (throw-illegal!)))
     (.accept name-class))
    (persistent! names)))

;; ## Extract value sets, e.g. for attributes

(defn has-name?
  [n xml-token]
  (->> (parse-names xml-token) (map str) (some #{n})))

(defn element?
  [name]
  (every-pred element-pattern? (partial has-name? name)))

(defn attribute?
  [name]
  (every-pred attribute-pattern? (partial has-name? name)))

(defn values
  [patterns]
  (for [pattern patterns :when (value-pattern? pattern)]
    (.getValue ^DValuePattern pattern)))

(defn attribute-values
  "Exracts values for a given element/attribute combination."
  [element-name attribute-name patterns]
  (let [element? (element? element-name)
        attribute? (attribute? attribute-name)]
    (for [element patterns :when (element? element)
          attribute (traverse-children element) :when (attribute? attribute)
          value (values (traverse attribute))]
      value)))

;; ## Identify container and value elements

(defn container-element?
  "Patterns, which only have XML tokens as child patterns, describe container
  elements, i. e. elements without text content (apart from whitespace)."
  [^DElementPattern pattern]
  (->> (traverse-children pattern)
       (remove xml-token-pattern?)
       (empty?)))

(defn value-element?
  [^DElementPattern pattern]
  (->>
   ;; traverse element content model, excluding attribute definitions
   (-> pattern (traverse (complement attribute-pattern?)) (rest))
   ;; remove attribute root nodes and any value patterns
   (remove (some-fn attribute-pattern? value-pattern?))
   ;; if nothing is left in the content model, this is a value element
   (empty?)))

(defn classify-elements
  "Returns a map, associating element names to boolean values: `true` when the
  named element fulfills the given `pred`, `false` otherwise."
  [pred grammar]
  (->> (traverse grammar)
       (filter element-pattern?)
       (map #(zipmap (->> % parse-names (map str)) (repeat (pred %))))
       (apply merge-with #(and %1 %2))))

(def container-elements
  (partial classify-elements container-element?))

(def value-elements
  (partial classify-elements value-element?))
