(ns dwdsox.metasearch
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [dwdsox.exist-db :as db]
            [clojure.data.xml :as xml]))

(def xquery-template (-> "dwdsox/xquery/metasearch.xq" io/resource slurp))

(def sample-query (-> xquery-template
                      (string/replace "[COLLECTION]" db/collection)
                      (string/replace "[FILTER]" "*")
                      (string/replace "[RESTRICTION]" "*")
                      (string/replace "[START]" "*")
                      (string/replace "[END]" "*")))

(defn init []
  (xml/parse-str (db/resource "indexedvalues.xml")))
