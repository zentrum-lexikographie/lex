(ns zdl-lex-wikimedia.wiktionary.history
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [clojure.tools.cli :refer [cli]]
            [zdl-lex-wikimedia.wiktionary :as wkt]))

(defn ^java.time.OffsetDateTime parse-instant
  [^String s]
  (java.time.OffsetDateTime/from
   (.. java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME
       (parse s))))

(defn revision->edit
  [{:keys [title username timestamp]}]
  {:title title
   :username username
   :timestamp (parse-instant timestamp)})

(defn anonymous? [{:keys [username]}]
  (nil? username))

(defn bot? [{:keys [username]}]
  (some? (re-seq #"[bB]ot" username)))

(defn in-2019? [{:keys [^java.time.OffsetDateTime timestamp]}]
  (= 2019 (.getYear timestamp)))

(defn edits [{:keys [last-year bots]} revisions]
  (let [filters (concat [anonymous?]
                      (when last-year [(complement in-2019?)])
                      (when (not bots) [bot?]))
        edits (map revision->edit revisions)]
    (remove (apply some-fn filters) edits)))

(defn edits->author-freqs [edits]
  (->> (map :username edits)
       (frequencies)
       (seq)
       (sort-by second #(compare %2 %1))))

(defn edits->years [edits]
  (->> (map :timestamp edits)
       (map #(.getYear ^java.time.OffsetDateTime %))
       (frequencies)
       (seq)
       (sort-by first)))

(defn -main [& args]
  (let [[opts [mode] help]
        (cli args
             ["-b" "--bots" "Include bots" :flag true :default false]
             ["-y" "--last-year" "Only edits of 2019" :flag true])]
    (try
      (condp = mode

        "authors"
        (wkt/with-wiktionary-history
          (->> revisions (edits opts) edits->author-freqs (csv/write-csv *out*)))

        "edits"
        (wkt/with-wiktionary-history
          (->> revisions (edits opts) edits->years (csv/write-csv *out*)))

        (println (str/join \newline
                           [""
                            "<cmd> [opts...] authors|edits"
                            ""
                            help
                            ""])))

      (finally (shutdown-agents)))))
