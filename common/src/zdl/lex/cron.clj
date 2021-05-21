(ns zdl.lex.cron
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [cronjure.core :as cron]
            [cronjure.definitions :as crondef]
            [manifold.deferred :as d]
            [manifold.executor :refer [execute-pool]]
            [manifold.stream :as s]
            [manifold.time :as mt])
  (:import java.time.Instant))

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

(defn ticker
  [cron-expr description]
  (let [schedule (cron/parse crondef/quartz cron-expr)
        s (s/stream)]
    (d/loop []
      (d/chain
       (mt/in (cron/time-to-next-execution schedule :millis)
              #(Instant/ofEpochMilli (System/currentTimeMillis)))
       (fn [v]
         (log/debugf "Tick '%s' @ %s", description v)
         (s/try-put! s v 0 {::dropped v}))
       (fn [put-or-dropped?]
         (when-let [v (get put-or-dropped? ::dropped)]
           (log/warnf "Dropped '%s' @ %s" description v ))
         (when put-or-dropped?
           (d/recur)))))
    (s/source-only s)))

(defn trigger
  [f description]
  (let [s (s/stream)
        f  #(d/catch
                (f)
                (fn [e] (log/warnf e "Trigger '%s' failed" description)))]
    (d/loop []
      (->
       (s/take! s)
       (d/onto (execute-pool))
       (d/chain
        (fn [v]
          (when v
            (log/infof "Triggered '%s' with %s", description v)
            (->
             (d/future (f))
             (d/catch (fn [e]
                        (log/warnf e "Error in '%s' with %s" description v)))
             (d/chain (fn [_] (d/recur)))))))))
    (s/sink-only s)))

(defn schedule-stream
  [expr description f]
  (let [ticks (ticker expr description)
        calls (trigger f description)]
    (s/connect ticks calls {:upstream? true :description "schedule"})
    (s/sink-only calls)))

(comment
  (let [test (schedule-stream
              "* * * * * ?"
              "Test"
              #(mt/in 2000 (constantly nil)))]
    @(mt/in 15000 #(s/close! test))))
