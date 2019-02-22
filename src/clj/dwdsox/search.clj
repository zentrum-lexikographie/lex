(ns dwdsox.search
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [dwdsox.basex :as db]
            [clojure.data.xml :as xml]))

(def xquery-prolog
  (string/join
   "\n"
   ["xquery version \"3.0\";"
    "declare namespace s=\"http://www.dwds.de/ns/1.0\";"]))

(def timestamp-types
  ["Artikel"
   "Formangabe"
   "Schreibung"
   "Grammatik"
   "Verweise"
   "Diachronie_V2"
   "Lesart"
   "Kollokationen"
   "Verwendungsbeispiele"])

(defn timestamp-xpath [{:keys [type from until]}]
  (str
   "/"
   (if type (str "/s:" type) "")
   "/@Zeitstempel"
   (if from (str "[.>='" from "']") "")
   (if until (str "[.<='" until "']") "")))

(defn xquery-for [timestamp-filter]
  (str
   "for $hit in "
   "collection(" db/collection ")"
   (timestamp-xpath timestamp-filter)))

(def xquery-order
  "order by $hit descending")

(def xquery-title
  "string-join($hit/ancestor::s:Artikel/s:Formangabe/s:Schreibung, ', ')")

(def xquery-definition
  (str
   "for $d in $hit/ancestor::s:Artikel/descendant::s:Definition "
   "return <def>{normalize-space(string-join($d//text(), ' '))}</def>"))

(def xquery-modified
  "data(db:list-details(db:name($hit), db:path($hit))/@modified-date)")

(def xquery-result
  (string/join
   "\n"
   ["return ("
    "<result>"
    "<title>{ " xquery-title " }</title>"
    "<status>{data($hit/ancestor::s:Artikel/@Status)}</status>"
    "<path>{db:path($hit)}</path>"
    (str "{ " xquery-definition " }")
    "<timestamp>{data($hit)}</timestamp>"
    "<timestampType>{$hit/parent::node()/name()}</timestampType>"
    (str "<modified>{ " xquery-modified " }</modified>")
    "</result>"
    ")"]))

(defn xquery-results [timestamp-filter]
  (string/join "\n" [(xquery-for timestamp-filter) xquery-order xquery-result]))

(defn xquery-result-page [page page-size timestamp-filter]
  (let [start (+ (* page page-size) 1)
        num-records page-size]
    (string/join
     "\n"
     ["let $results := "
      (xquery-results timestamp-filter)
      ""
      "return ("
      "<results>"
      (str "{subsequence($results, " start ", " num-records ")}")
      "<total>{count($results)}</total>"
      (str "<page>" page "</page>")
      (str "<pageSize>" page-size "</pageSize>")
      "</results>"
      ")"])))

(defn xquery [page page-size timestamp-filter]
  (string/join
   "\n\n"
   [xquery-prolog (xquery-result-page page page-size timestamp-filter)]))

(defn db-query []
  (db/simple-xml-query "" (xquery 0 5 {:from "2019-01-15"})))
