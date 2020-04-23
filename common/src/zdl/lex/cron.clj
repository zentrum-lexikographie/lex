(ns zdl.lex.cron
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [cronjure.core :as cron]
            [cronjure.definitions :as crondef]))

(def parse (partial cron/parse crondef/quartz))

(defn millis-to-next [instance]
  (cron/time-to-next-execution instance :millis))

(defn schedule
  ([cron-expr desc f]
   (schedule cron-expr desc f (a/chan (a/dropping-buffer 0))))
  ([cron-expr desc f results]
   (let [schedule (parse cron-expr)
         ctrl-ch (a/chan (a/dropping-buffer 1))]
     (a/go-loop []
       (when-let [req (a/alt! (a/timeout (millis-to-next schedule)) :scheduled
                              ctrl-ch ([v] v))]
         (log/info {:cron cron-expr :desc desc :req req})
         (if-let [result (a/<! (a/thread (f)))]
           (a/>! results result))
         (a/poll! ctrl-ch) ;; we just finished a job; remove pending req
         (recur)))
     ctrl-ch)))
