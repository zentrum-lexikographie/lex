(ns zdl-lex-validator.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [zdl-lex-common.args :as args]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.util :as util]
            [zdl-lex-common.xml-validate :as xv]))

(def ^:private usage
  [""
   "Validates XML files in one or more given directories against"
   "RELAXNG/Schematron rules applying to dictionary resources."
   ""
   "Usage: clojure.main -m zdl-lex-validator.core <dir> [<dir> …]"
   ""
   "If validation errors are encountered, they are written to standard"
   "output in CSV format."
   ""
   "Options:"])

(defn- parse-args
  "Parses command line arguments."
  [args]
  (args/parse
   [["-h" "--help"]]
   (fn [arguments options] (not-empty arguments))
   (fn [options-summary] (str/join \newline (conj usage options-summary)))
   args))

(def ^:private csv-header
  ["File" "Line" "Column" "Error Type" "Error Message"])

(defn- error->csv
  "Turns an error map into a CSV record."
  [{:keys [source line column type message]}]
  [source line column type message])

(def ^:private validate*
  "Validation function, allowing for parallel execution."
  (xv/create-validator (io/resource "DWDSWB.rng")
                       (io/resource "DWDSWB.sch.xsl")))

(defn- validate
  "Validates a file, replacing the absolute source path with a relative one."
  [[dir file]]
  (map #(assoc % :source file) (validate* (io/file dir file))))

(defn- xml-files
  "Lists all XML files in the given directory with relative paths."
  [dir]
  (let [dir (util/file dir)
        dir-path (.toPath dir)
        relativize (fn [f] [dir (.. dir-path (relativize (.toPath f)) (toString))])]
    (->> (article/article-xml-files dir)
         (map relativize))))

(defn -main
  "CLI entry point."
  [& args]
  (try
    (let [{dirs :arguments} (parse-args args)]
      (some->>
       dirs
       (mapcat xml-files)
       (pmap validate)
       (mapcat identity)
       (seq)
       (map error->csv)
       (cons csv-header)
       (csv/write-csv *out*)))
    (finally
      (shutdown-agents))))
