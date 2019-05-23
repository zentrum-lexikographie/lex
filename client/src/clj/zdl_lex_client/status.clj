(ns zdl-lex-client.status
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.http :as http]))

(defonce current (atom nil))

(defstate requests
  :start (let [ch (async/chan (async/sliding-buffer 1))]
           (async/go-loop []
             (try
               (let [status (async/<!
                             (async/thread
                               (http/get-edn #(merge % {:path "/status"}))))]
                 (reset! current (merge {:timestamp (t/now)} status)))
               (catch Exception e (timbre/warn e)))
             (when (async/alt! (async/timeout 10000) :tick ch ([v] v))
               (async/poll! ch)
               (recur)))
           ch)
  :stop (async/close! requests))
