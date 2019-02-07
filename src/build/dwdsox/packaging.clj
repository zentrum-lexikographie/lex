(ns dwdsox.packaging
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs]
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

(defn write-plugin-desc [version]
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0
      :xmlns:xt "http://www.oxygenxml.com/ns/extension"}
     (xsl/template {:match "@version[parent::plugin]"}
                   (xsl/attribute {:name "version"} version))))
   (io/file "src/oxygen/plugin/plugin.xml")
   (io/file "target/oxygen/plugins/dwdsox/plugin.xml")))

(defn package-plugin [version]
  (fs/delete-dir "target/oxygen/plugins")
  (fs/copy+ "src/oxygen/plugin.dtd" "target/oxygen/plugins/plugin.dtd")
  (fs/copy-dir "src/oxygen/plugin" "target/oxygen/plugins/dwdsox")
  (fs/copy+ (str "target/uberjar/dwdsox-" version "-standalone.jar")
            "target/oxygen/plugins/dwdsox/lib/dwdsox.jar")
  (write-plugin-desc version))

(defn -main
  "I don't do a whole lot ... yet."
  [version & args]
  (write-update-site-desc version)
  (package-plugin version))
