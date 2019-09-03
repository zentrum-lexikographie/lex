(ns zdl-lex-common.xml-validate
  (:require [clojure.java.io :as io]
            [zdl-lex-common.xml :as xml]
            [me.raynes.fs :as fs]
            [clojure.string :as str])
  (:import com.thaiopensource.relaxng.translate.Driver
           java.io.File
           javax.xml.transform.stream.StreamSource
           com.thaiopensource.util.PropertyMapBuilder
           [com.thaiopensource.validate ValidateProperty ValidationDriver]
           java.io.File
           javax.xml.transform.stream.StreamSource
           net.sf.saxon.s9api.XdmNode
           [org.xml.sax ErrorHandler SAXParseException]))

(defn rnc->rng [^File rnc ^File rng]
  (let [[rnc rng] (map #(.getAbsolutePath %) [rnc rng])]
    (when-not (= 0 (.run (Driver.) (into-array String [rnc rng])))
      (throw (ex-info "Error while converting RNC to RNG"
                      {:rnc rnc :rng rng})))))

(let [resource->xslt #(xml/compile-xslt (.toURI (io/resource %)))
      rng->sch (resource->xslt "schematron/ExtractSchFromRNG-2.xsl")
      sch->sch-xslt (resource->xslt "schematron/iso_svrl_for_xslt2.xsl")]
  (defn rng->sch-xslt [^File rng ^File sch-xslt]
    (let [sch (fs/temp-file "zdl-lex-common." ".sch")]
      (try
        (xml/xslt-transform rng->sch rng sch)
        (xml/xslt-transform sch->sch-xslt sch sch-xslt)
        (finally (fs/delete sch))))))

(let [xp-str #(comp str/join xml/nodes->seq (xml/xpath-fn %))
      xp-failures (xml/xpath-fn "//svrl:failed-assert")
      xp-failure-loc (xp-str "string(@location)")
      xp-failure-text (xp-str "svrl:text/text()")
      sch->error (fn [source doc failure]
                   (let [location (xp-failure-loc failure)
                         ^XdmNode node (->> ((xml/xpath-fn location) doc)
                                            (xml/nodes->seq)
                                            (first))]
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
  (defn validator-fn [rng-uri sch-xsl-uri]
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
                      (.loadSchema (xml/->input-source rng-uri)))
          schematron (xml/compile-xslt sch-xsl-uri)]
      (fn [src]
        (locking validator
          (try
            (reset! source src)
            (.validate validator (xml/->input-source src))
            (let [doc (.. xml/saxon-doc-builder (build src))
                  sch-failures (-> (xml/xslt-transform schematron doc) (xp-failures))
                  sch-errors (map (partial sch->error src doc) sch-failures)
                  rng-errors @errors]
              (reset! errors [])
              (concat rng-errors sch-errors))
            (catch SAXParseException e)))))))
