(ns zdl.lex.cron
  (:require [chime.core :as chime]
            [chime.core-async :as chime.async]
            [clojure.core.async :as a]
            [clojure.tools.logging :as log])
  (:import
   (java.time Duration LocalDateTime ZonedDateTime ZoneId)
   (java.time.temporal ChronoUnit)))

(defn today-at-hour
  [hour]
  (ZonedDateTime/of
   (.. (LocalDateTime/now) (truncatedTo ChronoUnit/DAYS) (withHour hour))
   (ZoneId/systemDefault)))

(defn periodic-seq
  [start duration]
  (->>
   (chime/periodic-seq start duration)
   (chime/without-past-times)))

(defn at-hour
  [hour]
  (periodic-seq (today-at-hour hour) (Duration/ofDays 1)))

(defn after-every
  [duration]
  (periodic-seq (today-at-hour 0) duration))

(comment
  (take 10 (at-hour 5))
  (take 10 (after-every (Duration/ofHours 4))))

(defn task-xf
  [desc f]
  (map (fn [ts]
         (log/infof "[%s] :: %s" ts desc)
         (let [result (f)]
           (if (nil? result) ts result)))))

(defn ex-handler
  [desc throwable]
  (log/warnf throwable "Error while executing scheduled task %s" desc))

(defn schedule
  ([desc times f]
   (schedule desc times f (a/chan (a/sliding-buffer 1))))
  ([desc times f results]
   (let [ch    (a/chan (a/sliding-buffer 1))
         ticks (chime.async/chime-ch times {:ch ch})]
     (a/pipeline-blocking
      1 results (task-xf desc f) ticks true #(ex-handler desc %))
     {:ticks    ticks
      :results  results
      :trigger! #(a/>!! ch (chime/now))})))

(defn trigger!
  [{:keys [trigger!]}]
  (trigger!))

(defn stop!
  [{:keys [ticks]}]
  (a/close! ticks))
