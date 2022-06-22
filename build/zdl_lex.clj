(ns zdl-lex
  (:require
   [gremid.data.xml.rnc :as dx.rnc]
   [gremid.data.xml.schematron :as dx.schematron]
   [clojure.tools.build.api :as b]
   [clojure.tools.build.tasks.copy :as copy]
   [clojure.java.io :as io]))

(def ignores
  (conj copy/default-ignores
        "^\\.env$"
        "^plugin/.*"
        "^plugin\\.dtd"
        "^project\\.xpr"))

(defn plugin-jar
  [_]
  (let [compile-basis (b/create-basis {:project "deps.edn"
                                       :aliases #{:client :oxygen :log}})
        package-basis (b/create-basis {:project "deps.edn"
                                       :aliases #{:client}})
        classes-dir   "classes/client"
        jar-file      "oxygen/plugin/lib/org.zdl.lex.client.jar"]
    (b/delete {:path jar-file})
    (b/delete {:path classes-dir})
    (b/copy-dir {:src-dirs   ["src" "oxygen"]
                 :target-dir classes-dir
                 :ignores    ignores})
    (b/compile-clj {:basis        compile-basis
                    :src-dirs     ["src"]
                    :ns-compile   ['zdl.lex.client.io
                                   'zdl.lex.client.oxygen
                                   'zdl.lex.client.plugin]
                    :compile-opts {:disable-locals-clearing true
                                   :elide-meta              []
                                   :direct-linking          false}
                    :class-dir    classes-dir})
    (b/uber {:class-dir classes-dir
             :uber-file jar-file
             :basis     package-basis})))


(defn transpile-schema
  [_]
  (let [framework-dir (io/file "oxygen" "framework")
        rnc (io/file framework-dir "rnc" "DWDSWB.rnc")
        rng (io/file framework-dir "rng" "DWDSWB.rng")
        sch (io/file framework-dir "rng" "DWDSWB.sch.xsl")]
    (dx.rnc/->rng rnc rng)
    (dx.schematron/extract-xslt rng sch)))
