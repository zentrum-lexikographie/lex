(ns zdl-lex-common.bus
  (:require [clojure.core.async :as a]))

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
