(ns dwdsox.packaging
  (:require [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [sigel.xslt.core :as xslt]
            [sigel.xslt.elements :as xsl]
            [sigel.xslt.components :as xslc]
            [clojure.java.io :as io]))

(def update-site-template (io/file "src/oxygen/updateSite.xml"))
(def update-site-xml (io/file "target/oxygen/updateSite.xml"))

(def update-site-stylesheet
  (xslt/compile-sexp
   (xslc/xslt3-identity
    {:version 3.0
     :xmlns:xt "http://www.oxygenxml.com/ns/extension"}
    (xsl/param {:name "version" :as "xs:string"})
    (xsl/template {:match "xt:version"}
                  [:xt:version (xsl/value-of {:select "$version"})]))))

(defn write-update-site-desc [version]
  (xslt/transform-to-file
   update-site-stylesheet {:version version}
   update-site-template update-site-xml))

(defn -main
  "I don't do a whole lot ... yet."
  [version & args]
  (write-update-site-desc version))
