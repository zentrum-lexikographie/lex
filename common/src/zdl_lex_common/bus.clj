(ns zdl-lex-common.bus
  (:require [clojure.core.async :as a]))

(defonce ^:private chan (a/chan))

(defonce ^:private pubs (a/pub chan first))

(defn publish! [topics v]
  (doseq [topic topics]
    (a/>!! chan [topic v])))

(defn listen [topics listener]
  (let [c (a/chan)]
    (doseq [topic topics]
      (a/sub pubs topic c))
    (a/go-loop []
      (when-let [msg (a/<! c)]
        (a/thread (apply listener msg))
        (recur)))
    (fn []
      (doseq [topic topics]
        (a/unsub pubs topic c))
      (a/close! c))))
