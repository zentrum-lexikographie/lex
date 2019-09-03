(ns zdl-lex-common.xml
  (:require [clojure.string :as str])
  (:import [java.io File StringReader StringWriter]
           [java.net URI URL]
           javax.xml.parsers.DocumentBuilderFactory
           [javax.xml.transform Result TransformerFactory URIResolver]
           javax.xml.transform.dom.DOMSource
           [javax.xml.transform.stream StreamResult StreamSource]
           net.sf.saxon.Configuration
           [net.sf.saxon.s9api Processor Serializer XdmValue XPathCompiler XPathExecutable XsltCompiler XsltExecutable]
           [org.w3c.dom Document NodeList]
           org.xml.sax.InputSource))

(def ^DocumentBuilderFactory doc-builder-factory
  (doto (DocumentBuilderFactory/newInstance)
    (.setNamespaceAware true)
    (.setExpandEntityReferences false)
    (.setXIncludeAware false)
    (.setValidating false)))

(defn ^javax.xml.parsers.DocumentBuilder new-document-builder []
  (.. doc-builder-factory (newDocumentBuilder)))

(defn ^Document new-document []
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

(def ^TransformerFactory transformer-factory
  (TransformerFactory/newInstance))

(defn serialize
  ([^Document doc ^Result result]
   (.. transformer-factory (newTransformer) (transform (DOMSource. doc) result)))
  ([^Document doc]
   (let [writer (StringWriter.)
         result (StreamResult. writer)]
     (serialize doc result)
     (str/replace (str writer)
                  #"(<\?xml version=\"1\.0\" encoding=\"UTF-8\"\?>)\s*"
                  "$1\n"))))

(defprotocol SAXInputSource
  (->input-source [src]))

(defn- ^InputSource uri->input-source [^URI uri]
  (InputSource. (str uri)))

(extend-protocol SAXInputSource
  File
  (->input-source [^File src] (-> (.toURI src) (uri->input-source)))
  URL
  (->input-source [^URL src] (-> (.toURI src) (uri->input-source)))
  URI
  (->input-source [^URI src] (uri->input-source src))
  String
  (->input-source [^String src] (uri->input-source (URI. src))))


(defn- ^URI resolve-uri [^URI base ^URI uri]
  "Resolves URIs, with support for the jar URL scheme."
  (if (= "jar" (.. base (getScheme)))
    (let [[base-jar base-path] (str/split (str base) #"!")
          resolved (.. (URI. base-path) (resolve uri))]
      (if-not (.isAbsolute resolved) (URI. (str base-jar "!" resolved)) resolved))
    (.resolve base uri)))

(def ^URIResolver uri-resolver
  "A URI resolver with support for resources from JARs on the classpath"
  (proxy [URIResolver] []
    (resolve [^String href ^String base]
      (let [base (URI. (or (not-empty base) ""))
            href (URI. (or (not-empty href) ""))]
        (StreamSource. (str (resolve-uri base href)))))))

(def ^Configuration saxon-configuration
  (doto (Configuration.)
    (.setURIResolver uri-resolver)))

(def ^Processor saxon-processor
  (Processor. saxon-configuration))

(defn ^Serializer file-serializer [^File f]
  (.newSerializer saxon-processor f))

(def ^net.sf.saxon.s9api.DocumentBuilder saxon-doc-builder
  (doto (.newDocumentBuilder saxon-processor)
    (.setLineNumbering true)))

(def ^XsltCompiler saxon-xslt-compiler
  (.newXsltCompiler saxon-processor))

(defn ^XsltExecutable compile-xslt [^URI stylesheet-uri]
  (.compile saxon-xslt-compiler (StreamSource. (str stylesheet-uri))))

(defn xslt-transform [^XsltExecutable stylesheet ^File source ^File destination]
  (doto (.load stylesheet)
    (.setSource (StreamSource. source))
    (.setDestination (file-serializer destination))
    (.transform)))

(def ^XPathCompiler xpath-compiler
  (doto (.newXPathCompiler saxon-processor)
    (.declareNamespace "ex" "http://exist.sourceforge.net/NS/exist")
    (.declareNamespace "d" "http://www.dwds.de/ns/1.0")
    (.declareNamespace "svrl" "http://purl.oclc.org/dsdl/svrl")))

(defn ^XPathExecutable compile-xpath [^String s]
  (.compile xpath-compiler s))

(defn ^XdmValue eval-xpath [^XPathExecutable xp ctx]
  (let [ctx (.. saxon-doc-builder (wrap ctx))]
    (.. (doto (.load xp) (.setContextItem ctx)) (evaluate))))

(defn xpath-fn [^String s]
  (partial eval-xpath (compile-xpath s)))

(defn nodes->seq [^NodeList nodes]
  (map #(.. nodes (item %)) (range (.getLength nodes))))

(comment
  (-> "<root/>" parse serialize))
