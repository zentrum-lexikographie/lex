(ns zdl.xml.cli
  (:gen-class)
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [zdl.xml.validate :as validate])
  (:import java.io.File
           java.nio.file.attribute.FileAttribute
           java.nio.file.Files))

(defn usage
  [{:keys [summary]}]
  (str/join
   \newline
   ["ZDL/XML Validation"
    "Copyright (C) 2020 Berlin-Brandenburgische Akademie der Wissenschaften"
    ""
    "Usage: java -jar zdl-xml.jar [options] <dir|*.xml>..."
    ""
    "Validates the given XML documents and/or directories containing"
    "XML documents (files ending in .xml). Errors are written to standard"
    "output in CSV format. Each error record has the following columns:"
    ""
    " 1. Path to XML file"
    " 2. Line number"
    " 3. Column number"
    " 4. Error type ('schema' or 'schematron')"
    " 5. Error message"
    ""
    "Options:"
    summary
    ""
    "This program is free software: you can redistribute it and/or modify"
    "it under the terms of the GNU General Public License as published by"
    "the Free Software Foundation, either version 3 of the License, or"
    "(at your option) any later version."
    ""
    "This program is distributed in the hope that it will be useful,"
    "but WITHOUT ANY WARRANTY; without even the implied warranty of"
    "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the"
    "GNU General Public License for more details."
    ""
    "You should have received a copy of the GNU General Public License"
    "along with this program. If not, see <https://www.gnu.org/licenses/>."]))

(defn error-msg
  [{:keys [errors] :as opts}]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline (conj errors "" (usage opts)))))

(defn exit
  ([status]
   (System/exit status))
  ([status msg]
   (println msg)
   (System/exit status)))

(def cli-options
  [["-r" "--rnc-schema RNC"
    "RELAX NG Schema (Compact syntax) to validate against"
    :parse-fn io/file]
   ["-x" "--rng-schema RNG"
    "RELAX NG Schema (XML syntax) to validate against"
    :parse-fn io/file]
   ["-s" "--schematron-xsl SCH"
    "Schematron XSL stylesheet to validate against"
    :parse-fn io/file]
   ["-h" "--help"]])

(defn parse-args
  [args]
  (let [{:keys [arguments options errors summary] :as opts}
        (parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage opts))
      (seq errors) (exit 1 (error-msg opts)))
    opts))

(def ^File temp-dir
  (let [file-attrs (make-array FileAttribute 0)
        temp-path (Files/createTempDirectory "zdl-xml." file-attrs)
        temp-file (. temp-path (toFile))]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. #(doseq [^File f (reverse (file-seq temp-file))] (.delete f))))
    temp-file))

(defn source->temp-dest
  [ext ^File source]
  (File. temp-dir
         (str (str/replace (.getName source) #"\.[^\.]*$" "") ext)))

(defn compile-schemas
  [^File rnc ^File rng ^File sch]
  (when (every? nil? [rnc rng sch])
    (exit 3 "Neither RNC/RNG nor Schematron source given."))
  (when (and rnc rng)
    (exit 3 "RNC and RNG source given, please provide only one."))
  (let [rng (or rng (source->temp-dest ".rng" rnc))
        rng-sch (source->temp-dest ".sch.xsl" rng)]
    (when rnc (validate/rnc->rng rnc rng))
    (when rng (validate/rng->sch-xslt rng rng-sch))
    (let [rng-sch? (not (validate/sch-xslt-empty? rng-sch))]
      (when-not rng-sch? (.delete rng-sch))
      [rng (if rng-sch? rng-sch sch)])))

(defn options->validate
  [{:keys [rnc-schema rng-schema schematron-xsl]}]
  (let [[rng sch] (compile-schemas rnc-schema rng-schema schematron-xsl)
        rng (if rng (validate/create-rng-validator rng))
        sch (if sch (validate/create-sch-validator sch))]
    (fn [f] (concat (if rng (rng f) []) (if sch (sch f) [])))))

(defn xml-file?
  [^File f]
  (-> (.getName f) (str/lower-case) (str/ends-with? ".xml")))

(defn file->xml-docs
  [^File f]
  (cond
    (.isFile f) [f]
    (.isDirectory f) (filter xml-file? (file-seq f))
    :else (exit 2 (str (str f) " does not exist"))))

(defn -main
  [& args]
  (try
    (let [{:keys [options arguments]} (parse-args args)
          validate (options->validate options)
          documents (mapcat file->xml-docs (map io/file arguments))]
      (doseq [errors (pmap validate documents)
              {:keys [source line column type message]} errors
              :let [type (name type)]]
        (csv/write-csv *out* [[source line column type message]]))
      (flush))
    (catch Throwable t
      (.printStackTrace t)
      (exit 4))
    (finally
      (shutdown-agents))))
