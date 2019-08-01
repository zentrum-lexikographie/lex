(ns zdl-lex-client.status
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.cron :as cron]
            [zdl-lex-client.http :as http]))

(defonce current (atom {:user "-"}))

(defstate requests
  :start (let [schedule (cron/parse "*/30 * * * * ?")
               ch (async/chan (async/sliding-buffer 1))]
           (async/go-loop []
             (try
               (let [status (async/<! (async/thread (http/get-status)))]
                 (reset! current (merge {:timestamp (t/now)} status)))
               (catch Exception e (timbre/warn e)))
             (when (async/alt! (async/timeout (cron/millis-to-next schedule)) :tick
                               ch ([v] v))
               (async/poll! ch)
               (recur)))
           ch)
  :stop (async/close! requests))
