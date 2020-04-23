(ns zdl.lex.timestamp
  (:refer-clojure :exclude [format]))

(defn ^String format
  "ISO-formats a date string."
  [^java.time.LocalDate d]
  (.. java.time.format.DateTimeFormatter/ISO_LOCAL_DATE (format d)))

(defn ^java.time.LocalDate parse
  "Parses an ISO-formatted date string."
  [s]
  (->>
   (.. java.time.format.DateTimeFormatter/ISO_LOCAL_DATE (parse s))
   (java.time.LocalDate/from)))

(defn ^String past
  "Yields a ISO timestamp, guaranteed to be today or in the past."
  [^String s]
  (let [now (format (java.time.LocalDate/now))]
    (try
      (let [ts (->> s parse format)
            valid? (<= (compare ts now) 0)]
        (if valid? ts now))
      (catch Throwable t now))))

(def ^:private unix-epoch (parse "1970-01-01"))

(defn days-since-epoch
  "The number of days since the UNIX epoch for a given date."
  [^java.time.LocalDate date]
  (.between java.time.temporal.ChronoUnit/DAYS unix-epoch date))

