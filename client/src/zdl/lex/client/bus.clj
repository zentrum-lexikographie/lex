(ns zdl.lex.client.bus
  (:require [zdl.lex.bus :as bus]
            [seesaw.bind :as uib]))

(def listen bus/listen)

(def publish! bus/publish!)

(defn bind [topics]
  (reify uib/Bindable
    (subscribe [_ handler]
      (listen topics (fn [& args] (handler args))))
    (notify [_ v]
      (publish! topics v))))



