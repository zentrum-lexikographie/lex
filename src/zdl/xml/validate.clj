(ns zdl.xml.validate
  "Validate XML documents via RELAX NG and Schematron."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [zdl.xml.util :as util])
  (:import com.thaiopensource.relaxng.translate.Driver
           com.thaiopensource.util.PropertyMapBuilder
           [com.thaiopensource.validate ValidateProperty ValidationDriver]
           java.io.File
           net.sf.saxon.s9api.XdmNode
           [org.xml.sax ErrorHandler SAXParseException]))

(defn rnc->rng
  "Transform files in RELAX NG Compact syntax into RELAX NG XML syntax."
  [^File rnc ^File rng]
  (let [[rnc rng] (map #(.getAbsolutePath %) [rnc rng])]
    (when-not (= 0 (.run (Driver.) (into-array String [rnc rng])))
      (throw (ex-info "Error while converting RNC to RNG"
                      {:rnc rnc :rng rng})))))

(defn- rng->error
  "Convert a RELAX NG error to an error record and add it to a collection."
  [source errors ^SAXParseException e]
  (let [message (.getMessage e)
        line (.getLineNumber e)
        column (.getColumnNumber e)]
    (swap! errors conj {:source @source
                        :line line
                        :column column
                        :type :schema
                        :message message})))

(defn create-rng-validator*
  "Creates a validation function based on a RELAX NG schema."
  [rng]
  (let [source (atom nil)
        errors (atom [])
        reset #(do (reset! source %) (reset! errors []))
        add-error (partial rng->error source errors)
        error-handler (proxy [ErrorHandler] []
                        (error [e] (add-error e))
                        (fatalError [e] (add-error e))
                        (warning [e] (add-error e)))
        properties (-> (doto (PropertyMapBuilder.)
                         (.put ValidateProperty/ERROR_HANDLER error-handler)
                         (.put ValidateProperty/URI_RESOLVER util/uri-resolver))
                       (.toPropertyMap))
        validator (doto (ValidationDriver. properties)
                    (.loadSchema (util/->input-source rng)))]
    (fn [src]
      (locking validator
        (reset src)
        (try
          (.validate validator (util/->input-source src))
          (catch SAXParseException e))
        (let [result @errors] (reset nil) result)))))

(defn create-rng-validator
  "Creates a thread-safe validation function based on a RELAX NG schema."
  [rng]
  (let [thread-locals (proxy [ThreadLocal] []
                        (initialValue [] (create-rng-validator* rng)))]
    (fn [src]
      ((.get thread-locals) src))))

(defn- resource->xslt
  "Compile XSLT stylesheet from classpath resource."
  [r]
  (util/->xslt (.toURI (io/resource r))))

(def ^:private rng->sch
  "Extract Schematron rules from RELAX NG stylesheet."
  (resource->xslt "zdl/xml/schematron/ExtractSchFromRNG-2.xsl"))

(def ^:private sch->sch-xslt
  "Compile Schematron rules into XSLT stylesheet."
  (resource->xslt "zdl/xml/schematron/iso_svrl_for_xslt2.xsl"))

(defn rng->sch-xslt
  "Derives a validating Schematron XSLT from a RELAX NG schema with embedded rules."
  [rng sch-xslt]
  (let [^File sch (File/createTempFile "zdl-xml." ".sch")]
    (try
      (util/transform rng->sch rng sch)
      (util/transform sch->sch-xslt sch sch-xslt)
      (finally (.delete sch)))))

(defn- xp-str
  "Creates a string-extracting function based on a XPath."
  [xp]
  (comp str/join (util/selector xp)))

(def ^:private xp-failures
  "Select Schematron failures."
  (util/selector ".//svrl:failed-assert"))

(def ^:private xp-failure-loc
  "Select Schematron failure locations."
  (xp-str "string(@location)"))

(def ^:private xp-failure-text
  "Select Schematron failure messages."
  (xp-str "svrl:text/text()"))

(defn sch-xslt-empty?
  [sch-xslt]
  (empty? (xp-failures sch-xslt)))

(defn- sch->error
  "Convert a Schematron failure to an error record."
  [source doc failure]
  (let [location (xp-failure-loc failure)
        selector (util/selector location)
        ^XdmNode node (-> doc selector first)]
    {:source source
     :line (.getLineNumber node)
     :column (.getColumnNumber node)
     :type :schematron
     :message (xp-failure-text failure)}))

(defn create-sch-validator
  "Creates a validation function based on a Schematron XSL stylesheet."
  [sch-xsl]
  (let [schematron (util/->xslt sch-xsl)]
    (fn [src]
      (let [doc (util/->xdm src)
            sch-report (util/transform schematron doc)
            sch-failures (xp-failures sch-report)
            sch-errors (map (partial sch->error src doc) sch-failures)]
        sch-errors))))
