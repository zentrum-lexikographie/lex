(ns zdl-lex-common.xml-validate
  "Validate XML documents representing lexicon entries."
  (:require [clojure.core.memoize :as memo]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.xml :as xml])
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

(defn- resource->xslt
  "Compile XSLT stylesheet from classpath resource."
  [r]
  (xml/->xslt (.toURI (io/resource r))))

(def ^:private rng->sch
  "Extract Schematron rules from RELAX NG stylesheet."
  (resource->xslt "schematron/ExtractSchFromRNG-2.xsl"))

(def ^:private sch->sch-xslt
  "Compile Schematron rules into XSLT stylesheet."
  (resource->xslt "schematron/iso_svrl_for_xslt2.xsl"))

(defn rng->sch-xslt
  "Derives a validating Schematron XSLT from a RELAX NG schema with embedded rules."
  [rng sch-xslt]
  (let [sch (fs/temp-file "zdl-lex-common." ".sch")]
    (try
      (xml/transform rng->sch rng sch)
      (xml/transform sch->sch-xslt sch sch-xslt)
      (finally (fs/delete sch)))))

(defn- xp-str
  "Creates a string-extracting function based on a XPath."
  [xp]
  (comp str/join (xml/selector xp)))

(def ^:private xp-failures
  "Select Schematron failures."
  (xml/selector ".//svrl:failed-assert"))

(def ^:private xp-failure-loc
  "Select Schematron failure locations."
  (xp-str "string(@location)"))

(def ^:private xp-failure-text
  "Select Schematron failure messages."
  (xp-str "svrl:text/text()"))

(defn- sch->error
  "Convert a Schematron failure to an error record."
  [source doc failure]
  (let [location (xp-failure-loc failure)
        selector (xml/selector location)
        ^XdmNode node (-> doc selector first)]
    {:source source
     :line (.getLineNumber node)
     :column (.getColumnNumber node)
     :type :schematron
     :message (xp-failure-text failure)}))

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

(defn create-validator*
  "Creates a validation function based on a RELAX NG schema an a Schematron
   stylesheet."
  [rng sch-xsl]
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
                           (.put ValidateProperty/URI_RESOLVER xml/uri-resolver))
                         (.toPropertyMap))
          validator (doto (ValidationDriver. properties)
                      (.loadSchema (xml/->input-source rng)))
          schematron (xml/->xslt sch-xsl)]
      (fn [src]
        (locking validator
          (try
            (reset src)
            (.validate validator (xml/->input-source src))
            (let [doc (xml/->xdm src)
                  sch-report (xml/transform schematron doc)
                  sch-failures (xp-failures sch-report)
                  sch-errors (map (partial sch->error src doc) sch-failures)
                  result (concat @errors sch-errors)]
              (reset nil)
              result)
            (catch SAXParseException e
              (let [result @errors] (reset nil) result)))))))

(defn create-validator
  [rng sch-xsl]
  (let [parallel (* 2 (.. Runtime getRuntime availableProcessors))
        cache (memo/lru (fn [_] (create-validator* rng sch-xsl)) {}
                        :lru/threshold parallel)]
    (fn [src]
      ((cache (Thread/currentThread)) src))))

(comment
  (let [validate (create-validator
                  (fs/file "../oxygen/framework/rng/DWDSWB.rng")
                  (fs/file "../oxygen/framework/rng/DWDSWB.sch.xsl"))
        articles (article/article-xml-files "../data/git/articles")]
    (time
     (->> articles
          (pmap validate)
          (mapcat identity)
          (last)))))
