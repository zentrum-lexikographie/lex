(ns zdl-lex-common.xml-validate
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [zdl-lex-common.xml :as xml])
  (:import com.thaiopensource.relaxng.translate.Driver
           com.thaiopensource.util.PropertyMapBuilder
           [com.thaiopensource.validate ValidateProperty ValidationDriver]
           java.io.File
           net.sf.saxon.s9api.XdmNode
           [org.xml.sax ErrorHandler SAXParseException]))

(defn rnc->rng [^File rnc ^File rng]
  (let [[rnc rng] (map #(.getAbsolutePath %) [rnc rng])]
    (when-not (= 0 (.run (Driver.) (into-array String [rnc rng])))
      (throw (ex-info "Error while converting RNC to RNG"
                      {:rnc rnc :rng rng})))))

(let [resource->xslt #(xml/->xslt (.toURI (io/resource %)))
      rng->sch (resource->xslt "schematron/ExtractSchFromRNG-2.xsl")
      sch->sch-xslt (resource->xslt "schematron/iso_svrl_for_xslt2.xsl")]
  (defn rng->sch-xslt [rng sch-xslt]
    (let [sch (fs/temp-file "zdl-lex-common." ".sch")]
      (try
        (xml/transform rng->sch rng sch)
        (xml/transform sch->sch-xslt sch sch-xslt)
        (finally (fs/delete sch))))))

(let [xp-str #(comp str/join (xml/selector %))
      xp-failures (xml/selector ".//svrl:failed-assert")
      xp-failure-loc (xp-str "string(@location)")
      xp-failure-text (xp-str "svrl:text/text()")
      sch->error (fn [source doc failure]
                   (let [location (xp-failure-loc failure)
                         selector (xml/selector location)
                         ^XdmNode node (-> doc selector first)]
                     {:source source
                      :line (.getLineNumber node)
                      :column (.getColumnNumber node)
                      :type :schematron
                      :message (xp-failure-text failure)}))
      rng->error (fn [source errors ^SAXParseException e]
                   (let [message (.getMessage e)
                         line (.getLineNumber e)
                         column (.getColumnNumber e)]
                     (swap! errors conj {:source @source
                                         :line line
                                         :column column
                                         :type :schema
                                         :message message})))]
  (defn validator [rng sch-xsl]
    (let [source (atom nil)
          errors (atom [])
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
            (reset! source src)
            (reset! errors [])
            (.validate validator (xml/->input-source src))
            (let [doc (xml/->xdm src)
                  sch-report (xml/transform schematron doc)
                  sch-failures (xp-failures sch-report)
                  sch-errors (map (partial sch->error src doc) sch-failures)]
              (concat @errors sch-errors))
            (catch SAXParseException e @errors)))))))

(comment
  (let [validate (validator (fs/file "../schema/resources/rng/DWDSWB.rng")
                            (fs/file "../schema/resources/rng/DWDSWB.sch.xsl"))]
    (validate (fs/file "../data/git/articles/DWDS/MWA-001/halbe Portion.xml"))))
