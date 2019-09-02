(ns zdl-lex-common.xml-validate
  (:require [clojure.java.io :as io]
            [zdl-lex-common.xml :as xml])
  (:import com.thaiopensource.relaxng.translate.Driver
           java.io.File
           javax.xml.transform.stream.StreamSource))

(defn rnc->rng [^File rnc ^File rng]
  (let [[rnc rng] (map #(.getAbsolutePath %) [rnc rng])]
    (when-not (= 0 (.run (Driver.) (into-array String [rnc rng])))
      (throw (ex-info "Error while converting RNC to RNG"
                      {:rnc rnc :rng rng})))))

(let [resource->xslt #(xml/compile-xslt (.toURI (io/resource %)))
      rng->sch (resource->xslt "schematron/ExtractSchFromRNG-2.xsl")
      sch->sch-xslt (resource->xslt "schematron/iso_svrl_for_xslt2.xsl")]
  (defn rng->sch-xslt [^File rng ^File sch-xslt]
    (let [sch->sch-xslt
          (doto (.load sch->sch-xslt)
            (.setDestination (xml/file-serializer sch-xslt)))
          rng->sch
          (doto (.load rng->sch)
            (.setSource (StreamSource. rng))
            (.setDestination sch->sch-xslt))]
      (.transform rng->sch)
      (.transform sch->sch-xslt))))
