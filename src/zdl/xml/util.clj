(ns zdl.xml.util
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer :all])
  (:import [java.io File
            InputStream OutputStream Reader StringReader StringWriter Writer]
           [java.net URI URL]
           javax.xml.parsers.DocumentBuilderFactory
           [javax.xml.transform Source URIResolver]
           javax.xml.transform.dom.DOMSource
           javax.xml.transform.stream.StreamSource
           net.sf.saxon.Configuration
           [net.sf.saxon.s9api
            DOMDestination Processor Serializer XdmDestination XdmItem
            XdmNode XdmValue XPathCompiler XPathExecutable XsltCompiler
            XsltExecutable]
           [org.w3c.dom Document Node NodeList]
           org.xml.sax.InputSource))

(defn ^URI resolve-uri [^URI base ^URI uri]
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

(def ^Configuration saxon-configuration
  (doto (Configuration.)
    (.setURIResolver uri-resolver)))

(def ^Processor saxon-processor
  (Processor. saxon-configuration))

(def ^net.sf.saxon.s9api.DocumentBuilder saxon-doc-builder
  (doto (.newDocumentBuilder saxon-processor)
    (.setLineNumbering true)))

(def ^XsltCompiler saxon-xslt-compiler
  (.newXsltCompiler saxon-processor))

(def ^XPathCompiler saxon-xpath-compiler
  (doto (.newXPathCompiler saxon-processor)
    (.declareNamespace "d" "http://www.dwds.de/ns/1.0")
    (.declareNamespace "svrl" "http://purl.oclc.org/dsdl/svrl")))

(defmulti ->source class)
(defmethod ->source Source
  ^Source [^Source s] s)
(defmethod ->source File
  ^Source [^File f] (StreamSource. f))
(defmethod ->source InputStream
  ^Source [^InputStream is] (StreamSource. is))
(defmethod ->source Document
  ^Source [^Document doc] (DOMSource. doc))
(defmethod ->source XdmNode
  ^Source [^XdmNode node] (.asSource node))
(defmethod ->source String
  ^Source [^String s] (StreamSource. s))
(defmethod ->source URI
  ^Source [^URI uri] (->source (str uri)))
(defmethod ->source URL
  ^Source [^URL url] (->source (.toURI url)))

(defmulti ->input-source class)
(defmethod ->input-source InputSource
  ^InputSource [^InputSource is] is)
(defmethod ->input-source InputStream
  ^InputSource [^InputStream is] (InputSource. is))
(defmethod ->input-source Reader
  ^InputSource [^Reader r] (InputSource. r))
(defmethod ->input-source String
  ^InputSource [^String s] (InputSource. s))
(defmethod ->input-source URI
  ^InputSource [^URI uri] (->input-source (str uri)))
(defmethod ->input-source URL
  ^InputSource [^URL url] (->input-source (.toURI url)))
(defmethod ->input-source File
  ^InputSource [^File f] (->input-source (.toURI f)))

(defmulti ->dom class)
(defmethod ->dom Node
  ^Node [^Node node] node)
(defmethod ->dom NodeList
  ^NodeList [^NodeList nl] nl)
(defmethod ->dom File
  ^Document [^File f] (.parse (new-document-builder) f))
(defmethod ->dom InputStream
  ^Document [^InputStream is] (.parse (new-document-builder) is))
(defmethod ->dom Reader
  ^Document [^Reader r] (.parse (new-document-builder) (->input-source r)))
(defmethod ->dom URI
  ^Document [^URI uri] (.parse (new-document-builder) (->input-source uri)))
(defmethod ->dom URL
  ^Document [^URL url] (->dom (.toURI url)))
(defmethod ->dom String
  ^Document [^String s] (->dom (StringReader. s)))
(defmethod ->dom XdmNode
  ^Document [^XdmNode node]
  (let [document (new-document)
        destination (DOMDestination. document)]
    (.writeXdmValue saxon-processor node destination)
    document))
(defmulti ->serializer class)
(defmethod ->serializer Serializer
  ^Serializer [^Serializer s] s)
(defmethod ->serializer File
  ^Serializer [^File f] (.. saxon-processor (newSerializer f)))
(defmethod ->serializer OutputStream
  ^Serializer [^OutputStream os] (.. saxon-processor (newSerializer os)))
(defmethod ->serializer Writer
  ^Serializer [^Writer w] (.. saxon-processor (newSerializer w)))
(defmethod ->serializer String
  ^Serializer [^String s] (->serializer (io/file s)))

(defmulti ->xdm class)
(defmethod ->xdm XdmValue
  ^XdmValue [^XdmValue v] v)
(defmethod ->xdm NodeList
  ^XdmValue [^NodeList nl] (.. saxon-doc-builder (wrap nl)))
(defmethod ->xdm Node
  ^XdmNode [^Node node] (.. saxon-doc-builder (wrap node)))
(defmethod ->xdm File
  ^XdmNode [^File f] (.. saxon-doc-builder (build f)))
(defmethod ->xdm Source
  ^XdmNode [^Source s] (.. saxon-doc-builder (build s)))
(defmethod ->xdm String
  ^XdmNode [^String s] (-> s ->source ->xdm))
(defmethod ->xdm URI
  ^XdmNode [^URI uri] (-> uri ->source ->xdm))
(defmethod ->xdm URL
  ^XdmNode [^URL url] (-> url ->source ->xdm))
(defmethod ->xdm InputStream
  ^XdmNode [^InputStream is] (-> is ->source ->xdm))
(defmethod ->xdm Reader
  ^XdmNode [^Reader r] (-> r ->source ->xdm))

(defmulti ->str class)
(defmethod ->str XdmItem
  ^String [^XdmItem item] (.getStringValue item))

(defmulti ->seq class)
(defmethod ->seq NodeList
  [^NodeList nl] (map #(.item nl %) (range (.getLength nl))))
(defmethod ->seq Node
  [^Node node] (->seq (.getChildNodes node)))
(defmethod ->seq XdmValue
  [^XdmValue v] v)

(doseq [multi-fn [->dom ->xdm ->seq]]
  (prefer-method multi-fn NodeList Node))

(defn ^XsltExecutable ->xslt [stylesheet]
  (.. saxon-xslt-compiler (compile (->source stylesheet))))

(defn ^XPathExecutable ->xpath [^String s]
  (.. saxon-xpath-compiler (compile s)))

(defn serialize
  ([source destination]
   (.. (->serializer destination) (serializeNode (->xdm source))))
  ([source]
   (let [writer (StringWriter.)]
     (serialize source writer)
     (str/replace (str writer)
                  #"(<\?xml version=\"1\.0\" encoding=\"UTF-8\"\?>)\s*"
                  "$1\n"))))

(defn transform
  ([^XsltExecutable stylesheet source]
   (let [destination (XdmDestination.)]
     (.. stylesheet (load30) (transform (->source source) destination))
     (.. destination (getXdmNode))))
  ([^XsltExecutable stylesheet source destination]
   (.. stylesheet (load30) (transform (->source source) (->serializer destination)))))

(defn ^XdmValue select
  [^XPathExecutable xp ctx]
  (.. (doto (.load xp) (.setContextItem (->xdm ctx))) (evaluate)))

(defn selector
  [^String s]
  (partial select (->xpath s)))

(defn normalize-space
  "Replaces whitespace runs with a single space and trims s."
  [s]
  (str/trim (str/replace s #"\s+" " ")))

(defn text
  "Returns non-empty, whitespace-normalized string."
  [s]
  (not-empty (normalize-space s)))

(deftest utilities
  (is (-> "<root/>" ->dom ->xdm))
  (is (->> "<root><a/></root>" ->dom ->seq (mapcat ->seq)))
  (is (-> "<root a=\"b\"/>" ->dom ->xdm ->dom serialize))
  (is (nil? (-> "<root/>" ->dom ->xdm ((selector "./test")) ->xdm seq))))


