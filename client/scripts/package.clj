(require '[clojure.java.io :as io])
(require '[me.raynes.fs :as fs])
(require '[clojure.data.xml :as xml])
(require '[sigel.xslt.core :as xslt])
(require '[sigel.xslt.elements :as xsl])
(require '[sigel.xslt.components :as xslc])
(require '[zdl-lex-client.env :refer [config]])

(import '[org.apache.commons.compress.archivers ArchiveStreamProvider ArchiveStreamFactory])

(defn zip [path dir prefix]
  (with-open [out (io/output-stream (io/file path))
              archive (.. (ArchiveStreamFactory.)
                          (createArchiveOutputStream "zip" out "UTF-8"))]
    (let [prefix-path (.toPath (io/file prefix))
          dir-path (.toPath dir)]
      (doseq [entry (file-seq dir)
              :let [file? (.isFile entry)
                    relative-path (.. dir-path (relativize (.toPath entry)))
                    archive-path (.. prefix-path (resolve relative-path))]]
        (->>
         (.createArchiveEntry archive entry (str archive-path))
         (.putArchiveEntry archive))
        (when file? (io/copy entry archive))
        (.closeArchiveEntry archive))
      (.finish archive))))

(defn -main [& args]
  (let [[_ version] args
        server-base (config :server-base)

        source (fs/file "src" "oxygen")

        schema-source (fs/file ".." "schema" "resources" "rng")
        schema-target (fs/file source "framework" "validation")

        target (fs/file "target")
        jar (fs/file target "uberjar" "zdl-lex-client.jar")
        oxygen (fs/file target "oxygen")

        plugins (fs/file oxygen "plugins")
        plugin (fs/file plugins "zdl-lex-client")
        plugin-zip (fs/file oxygen "zdl-lex-plugin.zip")

        frameworks (fs/file oxygen "frameworks")
        framework (fs/file frameworks "zdl-lex-client")
        framework-zip (fs/file oxygen "zdl-lex-framework.zip")

        update-site-xml (fs/file oxygen  "updateSite.xml")]

  (when-not (.isFile jar)
    (throw (IllegalStateException. (str "ZDL-Lex-Client not built: " jar))))

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

(when *command-line-args* (apply -main *command-line-args*))
