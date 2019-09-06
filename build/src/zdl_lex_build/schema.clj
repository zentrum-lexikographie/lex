(ns zdl-lex-build.schema
  (:require [zdl-lex-common.xml-validate :as xv]
            [me.raynes.fs :as fs]))

(defn -main [& args]
  (let [src-dir (-> "../schema/rnc" fs/file fs/absolute fs/normalized)
        dest-dir (-> "../schema/rng" fs/file fs/absolute fs/normalized)
        rnc (fs/file src-dir "DWDSWB.rnc")
        rng (fs/file dest-dir "DWDSWB.rng")
        sch-xslt (fs/file dest-dir "DWDSWB.sch.xsl")]
    (xv/rnc->rng (fs/file src-dir "DWDSWB.rnc")
                 (fs/file dest-dir "DWDSWB.rng"))
    (xv/rng->sch-xslt rng sch-xslt)))

