(ns zdl.lex.bus
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]))

(def ^:private chan
  (a/chan))

(def ^:private pubs
  (a/pub chan first))

(defn publish!
  [topics v]
  (doseq [topic topics]
    (a/>!! chan [topic v])))

(defn listen
  [topics listener]
  (let [c (a/chan)]
    (doseq [topic topics]
      (a/sub pubs topic c))
    (a/go-loop []
      (when-let [msg (a/<! c)]
        (try
          (apply listener msg)
          (catch Throwable t
            (log/warnf t "Error while processing %s" msg)))
        (recur)))
    (fn []
      (doseq [topic topics]
        (a/unsub pubs topic c))
      (a/close! c))))
