(ns zdl-lex-common.xml
  (:import [java.io StringReader StringWriter]
           javax.xml.parsers.DocumentBuilderFactory
           javax.xml.transform.dom.DOMSource
           javax.xml.transform.stream.StreamResult
           javax.xml.transform.TransformerFactory
           net.sf.saxon.Configuration
           [net.sf.saxon.s9api Processor XdmValue XPathCompiler XPathExecutable]
           org.xml.sax.InputSource))

(def ^DocumentBuilderFactory doc-builder-factory
  (doto (DocumentBuilderFactory/newInstance)
    (.setNamespaceAware true)
    (.setExpandEntityReferences false)
    (.setXIncludeAware false)
    (.setValidating false)))

(defn ^javax.xml.parsers.DocumentBuilder new-document-builder []
  (.. doc-builder-factory (newDocumentBuilder)))

(defn new-document []
  (.. (new-document-builder) (newDocument)))

(defprotocol Parseable
  (parse [this]))

(extend-protocol Parseable
  java.lang.String
  (parse [this]
    (.. (new-document-builder) (parse (InputSource. (StringReader. this)))))

  java.io.File
  (parse [this]
    (.. (new-document-builder) (parse this)))

  java.io.InputStream
  (parse [this]
    (.. (new-document-builder) (parse this))))

(def transformer-factory (TransformerFactory/newInstance))

(defn serialize
  ([doc result]
   (.. transformer-factory (newTransformer) (transform (DOMSource. doc) result)))
  ([doc]
   (let [writer (StringWriter.)
         result (StreamResult. writer)]
     (serialize doc result)
     (str writer))))

(def ^Processor saxon-processor (Processor. (Configuration.)))

(def ^net.sf.saxon.s9api.DocumentBuilder saxon-doc-builder
  (.newDocumentBuilder saxon-processor))

(def ^XPathCompiler xpath-compiler
  (doto (.newXPathCompiler saxon-processor)
    (.declareNamespace "ex" "http://exist.sourceforge.net/NS/exist")
    (.declareNamespace "d" "http://www.dwds.de/ns/1.0")))

(defn ^XPathExecutable compile-xpath [^String s]
  (.compile xpath-compiler s))

(defn ^XdmValue eval-xpath [^XPathExecutable xp ctx]
  (let [ctx (.. saxon-doc-builder (wrap ctx))]
    (.. (doto (.load xp) (.setContextItem ctx)) (evaluate))))

(defn xpath-fn [^String s]
  (partial eval-xpath (compile-xpath s)))

(comment
  (-> "<root/>" parse serialize))
