(ns zdl-lex-build.client
  (:require [me.raynes.fs :as fs]
            [sigel.xslt.components :as xslc]
            [sigel.xslt.core :as xslt]
            [sigel.xslt.elements :as xsl]
            [zdl-lex-build.zip :refer [zip]]))

(defn -main [& args]
  (let [version (slurp (fs/file "../VERSION"))

        client-base (-> "../client" fs/file fs/absolute fs/normalized)
        schema-base (-> "../schema" fs/file fs/absolute fs/normalized)

        source (fs/file client-base "src" "oxygen")

        css-source (fs/file schema-base "resources" "css")
        css-target (fs/file source "framework" "css")

        schema-source (fs/file schema-base "resources" "rng")
        schema-target (fs/file source "framework" "rng")

        target (fs/file client-base "target")
        jar (fs/file target "uberjar" "zdl-lex-client.jar")
        oxygen (fs/file target "oxygen")

        plugins (fs/file oxygen "plugins")
        plugin (fs/file plugins "zdl-lex-client")
        plugin-zip (fs/file oxygen "zdl-lex-plugin.zip")

        frameworks (fs/file oxygen "frameworks")
        framework (fs/file frameworks "zdl-lex-client")
        framework-zip (fs/file oxygen "zdl-lex-framework.zip")

        update-site-xml (fs/file oxygen "updateSite.xml")]

  (when-not (.isFile jar)
    (throw (IllegalStateException. (str "zdl-lex-client not built: " jar))))

  (fs/delete-dir plugins)
  (fs/copy+ (fs/file source "plugin.dtd") (fs/file plugins "plugin.dtd"))
  (fs/copy-dir (fs/file source "plugin") plugin)
  (fs/copy+ jar (fs/file plugin "lib" "zdl-lex-client.jar"))
  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0}
     (xsl/template {:match "@version[parent::plugin]"}
                   (xsl/attribute {:name "version"} version))
     (xsl/template {:match "runtime"}
                   [:runtime [:library {:name "lib/zdl-lex-client.jar"}]])))
   (fs/file source "plugin" "plugin.xml")
   (fs/file plugin "plugin.xml"))

  (zip plugin-zip plugin "zdl-lex-client")

  (fs/delete-dir css-target)
  (fs/copy-dir css-source css-target)

  (fs/delete-dir schema-target)
  (fs/copy-dir schema-source schema-target)

  (fs/delete-dir frameworks)
  (fs/copy-dir (fs/file source "framework") framework)

  (zip framework-zip framework "zdl-lex-client")

  (xslt/transform-to-file
   (xslt/compile-sexp
    (xslc/xslt3-identity
     {:version 3.0
      :xmlns:xt "http://www.oxygenxml.com/ns/extension"}
     (xsl/template {:match "xt:version"} [:xt:version version])))
   (fs/file source "updateSite.xml")
   (fs/file update-site-xml))))
