(ns zdl-lex-wikimedia.wiktionary.toponyms
  (:require [clojure.data.csv :as csv]
            [zdl-lex-wikimedia.wiktionary :as wkt]
            [zdl-lex-wikimedia.wiktionary.article :as wkt-article]))

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
    (wkt/with-current-wiktionary
      (->> (wkt-article/parse revisions)
           (wkt-article/german-entries)
           (wkt-article/types toponym?)
           (map toponym->csv)
           (filter has-derived?)
           (csv/write-csv *out*)))
    (finally (shutdown-agents))))

