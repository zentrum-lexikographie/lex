(ns zdl-lex-common.xml
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs])
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
    (.declareNamespace "ex" "http://exist.sourceforge.net/NS/exist")
    (.declareNamespace "d" "http://www.dwds.de/ns/1.0")
    (.declareNamespace "svrl" "http://purl.oclc.org/dsdl/svrl")))

(defprotocol Markup
  (->source [this])
  (->input-source [this])
  (->dom [this])
  (->serializer [this])
  (->xdm [this])
  (->seq [this]))

(extend-protocol Markup
  java.lang.String
  (->source [this]
    (StreamSource. this))
  (->input-source [this]
    (InputSource. this))
  (->dom [this]
    (.. (new-document-builder) (parse (InputSource. (StringReader. this)))))
  (->serializer [this]
    (->serializer (fs/file this)))
  (->xdm [this]
    (-> this ->source ->xdm))

  java.io.File
  (->source [this]
    (StreamSource. this))
  (->input-source [this]
    (->input-source (.. this (toURI))))
  (->dom [this]
    (.. (new-document-builder) (parse this)))
  (->serializer [this]
    (.. saxon-processor (newSerializer this)))
  (->xdm [this]
    (.. saxon-doc-builder (build this)))

  java.io.InputStream
  (->source [this]
    (StreamSource. this))
  (->input-source [this]
    (InputSource. this))
  (->dom [this]
    (.. (new-document-builder) (parse this)))
  (->xdm [this]
    (-> this ->source ->xdm))

  java.io.OutputStream
  (->serializer [this]
    (.. saxon-processor (newSerializer this)))

  java.io.Writer
  (->serializer [this]
    (.. saxon-processor (newSerializer this)))

  java.net.URI
  (->source [this]
    (->source (str this)))
  (->input-source [this]
    (->input-source (str this)))
  (->dom [this]
    (.. (new-document-builder) (parse (->input-source this))))
  (->xdm [this]
    (-> this ->source ->xdm))

  java.net.URL
  (->source [this]
    (->source (.. this (toURI))))
  (->input-source [this]
    (->input-source (.. this (toURI))))
  (->dom [this]
    (.. (new-document-builder) (parse (->input-source this))))
  (->xdm [this]
    (-> this ->source ->xdm))

  org.w3c.dom.Document
  (->source [this]
    (DOMSource. this))
  (->dom [this]
    this)
  (->xdm [this]
    (.. saxon-doc-builder (wrap this)))

  org.w3c.dom.Node
  (->xdm [this]
    (.. saxon-doc-builder (wrap this)))

  net.sf.saxon.s9api.XdmNode
  (->source [this]
    (.. this (asSource)))
  (->xdm [this]
    this)
  
  javax.xml.transform.Source
  (->source [this]
    this)
  (->xdm [this]
    (.. saxon-doc-builder (build this))))


(defn ^XsltExecutable ->xslt [stylesheet]
  (.. saxon-xslt-compiler (compile (->source stylesheet))))

(defn ^XPathExecutable ->xpath [^String s]
  (.. saxon-xpath-compiler (compile s)))

(defn ->seq [^NodeList nodes]
  (map #(.. nodes (item %)) (range (.getLength nodes))))

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
   (.. stylesheet (load30) (applyTemplates (->source source))))
  ([^XsltExecutable stylesheet source destination]
   (.. stylesheet (load30) (applyTemplates (->source source)
                                           (->serializer destination)))))

(defn ^XdmValue select [^XPathExecutable xp ctx]
  (.. (doto (.load xp) (.setContextItem (->xdm ctx))) (evaluate)))

(defn selector [^String s]
  (partial select (->xpath s)))

(comment
  (-> "<root/>" ->dom serialize))
