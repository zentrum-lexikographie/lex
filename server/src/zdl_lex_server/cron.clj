(ns zdl-lex-server.cron
  (:require [clojure.core.async :as a]
            [cronjure.core :as cron]
            [cronjure.definitions :as crondef]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [mount.core :as mount]))

(def parse (partial cron/parse crondef/quartz))

(defn millis-to-next [instance]
  (cron/time-to-next-execution instance :millis))

(defn schedule
  ([cron-expr desc f]
   (schedule cron-expr desc f (a/chan (a/dropping-buffer 0))))
  ([cron-expr desc f results]
   (let [schedule (parse cron-expr)
         ctrl-ch (a/chan)]
     (a/go-loop []
       (when-let [req (a/alt! (a/timeout (millis-to-next schedule)) :scheduled
                              ctrl-ch ([v] v))]
         (timbre/info {:cron cron-expr :desc desc :req req})
         (if-let [result (a/<! (a/thread (f)))]
           (a/>! results result))
         (a/poll! ctrl-ch) ;; we just finished a job; remove pending req
         (recur)))
     ctrl-ch)))
