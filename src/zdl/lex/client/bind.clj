(ns zdl.lex.client.bind
  (:require [zdl.lex.bus :as bus]
            [seesaw.bind :as uib]))

(defn bind->bus
  [topics]
  (reify uib/Bindable
    (subscribe [_ handler]
      (bus/listen topics (fn [topic message] (handler [topic message]))))
    (notify [_ message]
      (bus/publish! topics message))))


