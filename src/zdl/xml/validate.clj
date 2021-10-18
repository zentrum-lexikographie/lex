(ns zdl.xml.validate
  "Validate XML documents via RELAX NG and Schematron."
  (:require [clojure.string :as str]
            [zdl.xml.util :as util])
  (:import javax.xml.XMLConstants
           javax.xml.validation.SchemaFactory
           net.sf.saxon.s9api.XdmNode
           [org.xml.sax ErrorHandler SAXParseException]))

(defn configure-jing-schema-factory!
  []
  (System/setProperty
   (str (.getName SchemaFactory) ":" XMLConstants/RELAXNG_NS_URI)
   "com.thaiopensource.relaxng.jaxp.XMLSyntaxSchemaFactory"))

(defn rng-schema-factory
  []
  (try
    (SchemaFactory/newInstance XMLConstants/RELAXNG_NS_URI)
    (catch IllegalArgumentException _
      (configure-jing-schema-factory!)
      (SchemaFactory/newInstance XMLConstants/RELAXNG_NS_URI))))

(defn rng->schema
  [rng]
  (.newSchema (rng-schema-factory) (util/->source rng)))

(defn rng->error
  "Convert a RELAX NG error to an error record and add it to a collection."
  [errors severity ^SAXParseException e]
  (let [message (.getMessage e)
        line    (.getLineNumber e)
        column  (.getColumnNumber e)]
    (swap! errors conj {:line     line
                        :column   column
                        :severity severity
                        :message  message})))

(defn rng-validate
  [schema source]
  (let [validator     (.newValidator schema)
        errors        (atom [])
        add-error     (partial rng->error errors)
        error-handler (proxy [ErrorHandler] []
                         (error [e] (add-error :error e))
                         (fatalError [e] (add-error :fatal e))
                         (warning [e] (add-error :warning e)))]
    (.setErrorHandler validator error-handler)
    (.validate validator (util/->source source))
    @errors))

(defn- xp-str
  "Creates a string-extracting function based on a XPath."
  [xp]
  (comp str/join (util/selector xp)))

(def xp-failures
  "Select Schematron failures."
  (util/selector ".//svrl:failed-assert"))

(def xp-failure-loc
  "Select Schematron failure locations."
  (xp-str "string(@location)"))

(def xp-failure-text
  "Select Schematron failure messages."
  (xp-str "svrl:text/text()"))

(defn- sch->error
  "Convert a Schematron failure to an error record."
  [doc failure]
  (let [location (xp-failure-loc failure)
        selector (util/selector location)
        ^XdmNode node (-> doc selector first)]
    {:line (.getLineNumber node)
     :column (.getColumnNumber node)
     :message (xp-failure-text failure)}))

(defn create-sch-validator
  "Creates a validation function based on a Schematron XSL stylesheet."
  [sch-xsl]
  (let [schematron (util/->xslt sch-xsl)]
    (fn [source]
      (let [doc (util/->xdm source)
            sch-report (util/transform schematron doc)
            sch-failures (xp-failures sch-report)
            sch-errors (map (partial sch->error doc) sch-failures)]
        sch-errors))))
