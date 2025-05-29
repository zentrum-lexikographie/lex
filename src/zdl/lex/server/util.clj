(ns zdl.lex.server.util
  (:require
   [clojure.core.async :as a]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   (java.text Normalizer Normalizer$Form)
   (java.time Instant LocalDate ZoneOffset)
   (java.time.format DateTimeFormatter)
   (java.util Locale)))

(defn norm-str
  [s]
  (some-> s str str/trim not-empty))

(defn norm-str-coll
  [coll]
  (seq (sequence (comp (map norm-str) (remove nil?)) coll)))

(defn norm-str-set
  [coll]
  (some->> coll (norm-str-coll) (into (sorted-set))))

(defn parse-year
  [s]
  (when (and s (re-matches #"[12][0-9]{3}" s)) (parse-long s)))

(defn parse-date
  [s]
  (when-let [^String s (norm-str s)]
    (try (LocalDate/parse s) (catch Throwable _))))

(defn date->year
  [^LocalDate d]
  (when d (.getYear d)))

(def date-formatter
  (DateTimeFormatter/ofPattern "dd.MM.yyyy" Locale/GERMAN))

(defn format-date
  [^LocalDate d]
  (when d (.format ^DateTimeFormatter date-formatter d)))

(defn parse-timestamp
  [s]
  (when-let [^String s (norm-str s)]
    (try
      (Instant/parse s)
      (catch Throwable _))))

(defn timestamp->date
  [^Instant ts]
  (when ts
    (try
      (.. ts (atOffset ZoneOffset/UTC) (toLocalDate))
      (catch Throwable _))))

(defn assoc*
  [m k v]
  (cond-> m (some? v) (assoc k v)))

(defn pr-edn
  [& args]
  (binding [*print-length*   nil
            *print-dup*      nil
            *print-level*    nil
            *print-readably* true]
    (apply pr args)))

(defn pr-edn-str
  [& args]
  (with-out-str
    (apply pr-edn args)))

(defn spit-edn
  [f & args]
  (with-open [w (io/writer (io/file f))]
    (binding [*out* w]
      (apply pr-edn args))))

(defn slurp-edn
  [f]
  (read-string (slurp (io/file f))))

(defn form->id
  [form]
  (-> form
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"\p{InCombiningDiacriticalMarks}" "")
      (str/replace "ß" "ss")
      (str/replace " " "_")
      (str/replace #"[^\p{Alpha}\p{Digit}\-_]" "_")
      (str/replace #"_+" "_")))

(defn merge-chs
  [chs]
  (let [ch (a/chan)]
    (a/go-loop [chs chs]
      (if (seq chs)
        (let [[v ch*] (a/alts! chs)]
          (if (nil? v)
            (recur (filterv #(not= ch* %) chs))
            (if (a/>! ch v)
              (recur chs)
              (run! a/close! (cons ch chs)))))
        (a/close! ch)))
    ch))
