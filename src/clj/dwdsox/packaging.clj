(ns dwdsox.packaging
  (:require [clojure.java.io :as io]
            [sigel.xslt.core :as xslt]
            [sigel.xslt.elements :as xsl]
            [sigel.xslt.components :as xslc]))

(defn write-update-site-desc [version]
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0
      :xmlns:xt "http://www.oxygenxml.com/ns/extension"}
     (xsl/template {:match "xt:version"} [:xt:version version])))
   (io/file "src/oxygen/updateSite.xml")
   (io/file "target/oxygen/updateSite.xml")))

(defn -main
  "I don't do a whole lot ... yet."
  [version & args]
  (write-update-site-desc version))
