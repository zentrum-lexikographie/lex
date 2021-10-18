(ns zdl.lex.build.rng
  "Validate XML documents via RELAX NG and Schematron."
  (:require [clojure.java.io :as io]
            [zdl.xml.util :as util])
  (:import com.thaiopensource.relaxng.translate.Driver
           java.io.File))

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
  (util/->xslt (.toURI (io/resource r))))

(def rng->sch
  "Extract Schematron rules from RELAX NG stylesheet."
  (resource->xslt "zdl/xml/schematron/ExtractSchFromRNG-2.xsl"))

(def sch->sch-xslt
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
