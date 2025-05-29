(ns zdl.lex.server.qa
  (:require [clojure.java.io :as io]
            [gremid.xml-schema :as gxs]
            [zdl.lex.article.qa :as qa]
            [babashka.fs :as fs]))

(def rng-validate
  (->> (gxs/->rng-schema (fs/file "oxygen" "framework" "rng" "DWDSWB.rng"))
       (partial gxs/rng-validate)))

(def sch-validate
  (->> (gxs/->xslt (fs/file  "oxygen" "framework" "rng" "DWDSWB.sch.xsl"))
       (partial gxs/sch-validate)))

(defn check-for-errors
  [xml file]
  {:errors
   (cond-> []
     (seq (qa/check-typography xml)) (conj "Typographie")
     (seq (rng-validate file))       (conj "Schema")
     (seq (sch-validate file))       (conj "Schematron"))})

(comment
  (rng-validate (io/file "src" "template.xml"))
  (sch-validate (io/file "src" "template.xml")))
