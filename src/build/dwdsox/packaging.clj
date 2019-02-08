(ns dwdsox.packaging
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [sigel.xslt.core :as xslt]
            [sigel.xslt.elements :as xsl]
            [sigel.xslt.components :as xslc]))

(def jar "target/uberjar/oxygen-extensions.jar")

(defn package-plugin [version]
  (fs/delete-dir "target/oxygen/plugins")
  (fs/copy+ "src/oxygen/plugin.dtd" "target/oxygen/plugins/plugin.dtd")
  (fs/copy-dir "src/oxygen/plugin" "target/oxygen/plugins/dwdsox")
  (fs/copy+ jar "target/oxygen/plugins/dwdsox/lib/oxygen-extensions.jar")
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0}
     (xsl/template {:match "@version[parent::plugin]"}
                   (xsl/attribute {:name "version"} version))
     (xsl/template {:match "runtime"}
                   [:runtime [:library {:name "lib/oxygen-extensions.jar"
                                        :scope "global"}]])))
   (io/file "src/oxygen/plugin/plugin.xml")
   (io/file "target/oxygen/plugins/dwdsox/plugin.xml")))

(defn package-framework [version]
  (fs/delete-dir "target/oxygen/frameworks")
  (fs/copy-dir "src/oxygen/framework" "target/oxygen/frameworks/dwdsox")
  (fs/copy+ jar "target/oxygen/frameworks/dwdsox/lib/oxygen-extensions.jar")
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0}
     (xsl/template {:match "field[@name='classpath']"}
                   [:field {:name "classpath"}
                    [:String-array
                     [:String "${frameworkDir}/lib/oxygen-extensions.jar"]]])))
   (io/file "src/oxygen/framework/DWDS.framework")
   (io/file "target/oxygen/frameworks/dwdsox/DWDS.framework")))

(defn -main
  "I don't do a whole lot ... yet."
  [version & args]
  (when-not (-> jar (io/file) (.isFile))
    (throw (IllegalStateException. (str "Oxygen Extensions not built: " jar))))
  (package-plugin version)
  (package-framework version)
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0
      :xmlns:xt "http://www.oxygenxml.com/ns/extension"}
     (xsl/template {:match "xt:version"} [:xt:version version])))
   (io/file "src/oxygen/updateSite.xml")
   (io/file "target/oxygen/updateSite.xml")))
