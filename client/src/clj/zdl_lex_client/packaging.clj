(ns zdl-lex-client.packaging
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [sigel.xslt.core :as xslt]
            [sigel.xslt.elements :as xsl]
            [sigel.xslt.components :as xslc]
            [zdl-lex-client.env :refer [config]]))

(def server-base (config :server-base))

(def jar "target/uberjar/zdl-lex-client.jar")
(def target-dir "target/oxygen")

(def plugins-dir (str target-dir "/plugins"))
(def plugin-dir (str plugins-dir "/zdl-lex-client"))

(def frameworks-dir (str target-dir "/frameworks"))
(def framework-dir (str frameworks-dir "/zdl-lex-client"))

(def update-site-xml (str target-dir "/updateSite.xml"))

(defn package-plugin [version]
  (fs/delete-dir plugins-dir)
  (fs/copy+ "src/oxygen/plugin.dtd" (str plugins-dir "/plugin.dtd"))
  (fs/copy-dir "src/oxygen/plugin" plugin-dir)
  (fs/copy+ jar (str plugin-dir "/lib/zdl-lex-client.jar"))
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0}
     (xsl/template {:match "@version[parent::plugin]"}
                   (xsl/attribute {:name "version"} version))
     (xsl/template {:match "runtime"}
                   [:runtime [:library {:name "lib/zdl-lex-client.jar"
                                        :scope "global"}]])))
   (io/file "src/oxygen/plugin/plugin.xml")
   (io/file (str plugin-dir "/plugin.xml"))))

(defn package-framework [version]
  (fs/delete-dir frameworks-dir)
  (fs/copy-dir "src/oxygen/framework" framework-dir)
  (fs/copy+ jar (str framework-dir "/lib/zdl-lex-client.jar"))
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0}
     (xsl/template {:match "text()[contains(., 'http://localhost:8984/')]"}
                   (xsl/value-of
                    {:select (str "replace(., 'http://localhost:8984/', '"
                                  server-base
                                  "')")}))
     (xsl/template {:match "field[@name='classpath']"}
                   [:field {:name "classpath"}
                    [:String-array
                     [:String "${frameworkDir}/lib/zdl-lex-client.jar"]]])))
   (io/file "src/oxygen/framework/DWDS.framework")
   (io/file (str framework-dir "/DWDS.framework"))))

(defn update-site [version]
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0
      :xmlns:xt "http://www.oxygenxml.com/ns/extension"}
     (xsl/template {:match "xt:version"} [:xt:version version])))
   (io/file "src/oxygen/updateSite.xml")
   (io/file update-site-xml)))

(defn -main [version & args]
  (when-not (-> jar (io/file) (.isFile))
    (throw (IllegalStateException. (str "ZDL-Lex-Client not built: " jar))))
  (package-plugin version)
  (package-framework version)
  (update-site version))
