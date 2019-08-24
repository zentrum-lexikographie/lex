(ns zdl-lex-client.bus
  (:require [clojure.core.async :as a]
            [seesaw.bind :as uib]))

(defonce ^:private chan (a/chan))

(defonce ^:private pubs (a/pub chan first))

(defn publish! [topic v]
  (a/>!! chan [topic v]))

(defn listen [topic listener]
  (let [c (a/chan)]
    (a/sub pubs topic c)
    (a/go-loop []
      (when-let [msg (a/<! c)]
        (a/thread (listener (second msg)))
        (recur)))
    (partial a/close! c)))

(defn bind [topic]
  (reify uib/Bindable
    (subscribe [_ handler]
      (listen topic handler))
    (notify [_ v]
      (publish! topic v))))



