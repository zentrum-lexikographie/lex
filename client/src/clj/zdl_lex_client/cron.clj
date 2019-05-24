(ns zdl-lex-client.cron
  (:require [cronjure.core :as cron]
            [cronjure.definitions :as crondef]))

(def parse (partial cron/parse crondef/quartz))

(defn millis-to-next [instance]
  (cron/time-to-next-execution instance :millis))
