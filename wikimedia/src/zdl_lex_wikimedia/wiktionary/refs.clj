(ns zdl-lex-wikimedia.wiktionary.refs
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [clojure.tools.cli :refer [cli]]
            [zdl-lex-wikimedia.dump :as dump]
            [zdl-lex-wikimedia.wikitext :as wt]
            [zdl-lex-wikimedia.wiktionary.de :as de-wkt]
            [clojure.zip :as zip]
            [clojure.data.zip :as dz])
  (:import [org.sweble.wikitext.parser.nodes WtTemplate WtName WtTagExtension WtTagExtensionBody]))

(defn ref? [loc]
  (= "ref" (-> loc zip/node .getName)))

(defn ref-content [loc]
  (-> loc zip/node .getBody .getContent wt/parse wt/zipper list))

(defn per-template? [loc]
  (-> loc zip/node .getName .getAsString (str/starts-with? "Per-")))

(defn per-name [loc]
  (-> loc zip/node .getName .getAsString (str/replace #"Per-" "") str/trim))

(defn parse-references [{:keys [title text] :as revision}]
  (if-let [loc (some-> text wt/parse wt/zipper)]
    (->> (wt/nodes-> loc dz/descendants
                     WtTagExtension ref? ref-content dz/descendants
                     WtTemplate [per-template?] per-name)
         (assoc revision :references))
    revision))

(defn -main [& args]
  (try
    (dump/with-revisions de-wkt/current-page-dump
      (->> (filter de-wkt/article? revisions)
           (map parse-references)
           (mapcat :references)
           (frequencies)
           (seq)
           (sort-by second #(compare %2 %1))
           (csv/write-csv *out*)))
    (finally (shutdown-agents))))
