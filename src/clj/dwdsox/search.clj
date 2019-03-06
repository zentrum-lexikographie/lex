(ns dwdsox.search
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.data.xml :as xml]
            [dwdsox.db :as db]))

(def filters
  (-> (io/resource "dwdsox/search-facets.edn")
      (slurp :encoding "UTF-8")
      edn/read-string))

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

(defn xq-join [& lines] (string/join "\n" lines))

(defn xq-escape-str [s]
  (string/replace s "\"" "\\\""))

(defn xq-escape-regex [re]
  (string/replace re #"([.\"])" "\\\\$0"))

(defn timestamp-expr [[{:keys [element from until]}]]
  (str
   (cond
     (= "Artikel" element) "/"
     (string? element) (str "//s:" element "/")
     :else "//")
   "@Zeitstempel"
   (if from (str "[.>='" from "']") "")
   (if until (str "[.<='" until "']") "")))

(defn meta-exprs [filters]
  (map (fn [{:keys [xpath value]}]
         (str "$article[" xpath "=\"" (xq-escape-str value) "\"]")) filters))

(defn content-exprs [filters]
  (map (fn [{:keys  [xpath value]}]
         (str "$article[" xpath " contains text \""
              (.replace (xq-escape-regex value) "*" ".*")
              "\" using wildcards]")) filters))

(def article-title
  "string-join($article/s:Formangabe/s:Schreibung, ', ')")

(def article-definition
  (str
   "for $d in $article//s:Definition "
   "return <def>{normalize-space(string-join($d//text(), ' '))}</def>"))

(def article-modified
  "data(db:list-details(db:name($article), db:path($article))/@modified-date)")

(def article-path
  (str "replace(base-uri($article), '" db/collection "/', '')"))

(defn articles [collection filters filter-op page page-size]
  (let [filters-by-type (group-by :type filters)

        filter-exprs (concat
                      (meta-exprs (filters-by-type :meta))
                      (content-exprs (filters-by-type :content)))

        filter-junction (if (= filter-op :or) " or " " and ")

        articles (xq-join
                  "for $article in //s:Artikel"
                  (str "let $ts := sort($article"
                       (timestamp-expr (filters-by-type :timestamp))
                       ")[last()]")
                  "order by $ts descending"
                  (str "where $ts and (" (string/join filter-junction filter-exprs) ")")
                  "return ("
                  "<article>"
                  (str "<title>{ " article-title " }</title>")
                  "<source>{ data($article/@Quelle) }</source>"
                  "<status>{ data($article/@Status) }</status>"
                  (str "<path>{ " article-path " }</path>")
                  (str "{ " article-definition " }")
                  "<author>{ data($article/@Autor) }</author>"
                  "<ts type=\"{ $ts/parent::node()/name() }\">{ data($ts) }</ts>"
                  "</article>"
                  ")")

        start (+ (* page page-size) 1)

        article-page (xq-join
                      (str "let $articles := " articles)
                      "return ("
                      "<articles>"
                      "<total>{ count($articles) }</total>"
                      (str "{subsequence($articles, " start ", " page-size ")}")
                      "</articles>"
                      ")")]
    (db/query
     collection
     (xq-join
      "xquery version \"3.1\";"
      "declare namespace s=\"http://www.dwds.de/ns/1.0\";"
      article-page))))

(def sample-query
  [{:type :timestamp :element "Artikel" :until "2018-01-01"}
   {:type :meta
    :title "Quelle"
    :xpath "@Quelle"
    :value "DWDS"}
   {:type :content
    :title "Schreibung",
    :xpath "*:Formangabe/*:Schreibung"
    :value "*lexiko*"}])

(def simple-sample-query
  [{:type :timestamp :element "Artikel" :until "2018-01-01"}
   {:type :meta
    :title "Quelle"
    :xpath "@Quelle"
    :value "DWDS"}])
