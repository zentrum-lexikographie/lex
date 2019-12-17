(ns zdl-lex-wikimedia.wiktionary.toponyms
  (:require [clojure.data.csv :as csv]
            [zdl-lex-wikimedia.dump :as dump]
            [zdl-lex-wikimedia.wiktionary.de :as de-wkt]))

(defn toponym?
  [{:keys [pos-set]}]
  (some #{"Toponym"} (or pos-set #{})))

(defn toponym->csv
  [{:keys [title derived]}]
  (concat [title] (some->> derived (mapcat :links))))

(defn has-derived?
  [csv]
  (< 1 (count csv)))

(defn -main [& args]
  (try
    (dump/with-revisions de-wkt/current-page-dump
      (->> (de-wkt/parse-revisions revisions)
           (de-wkt/german-entries)
           (de-wkt/types toponym?)
           (map toponym->csv)
           (filter has-derived?)
           (csv/write-csv *out*)))
    (finally (shutdown-agents))))

