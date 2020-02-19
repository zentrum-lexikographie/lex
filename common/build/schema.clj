(ns schema
  (:require [zdl-lex-common.util :refer [file]]
            [zdl-xml.validate :as xv]
            [me.raynes.fs :as fs]))

(defn -main [& args]
  (let [src-dir (file "../oxygen/framework/rnc")
        dest-dir (file "../oxygen/framework/rng")
        rnc (file src-dir "DWDSWB.rnc")
        rng (file dest-dir "DWDSWB.rng")
        sch-xslt (file dest-dir "DWDSWB.sch.xsl")]
    (fs/delete-dir dest-dir)
    (fs/mkdirs dest-dir)
    (xv/rnc->rng rnc rng)
    (xv/rng->sch-xslt rng sch-xslt)))

