(ns zdl-lex-client.bus
  (:require [zdl-lex-common.bus :as bus]
            [seesaw.bind :as uib]))

(def listen bus/listen)

(def publish! bus/publish!)

(defn bind [topic]
  (reify uib/Bindable
    (subscribe [_ handler]
      (listen topic handler))
    (notify [_ v]
      (publish! topic v))))



