(ns zdl.lex.client.bus
  (:require [clojure.tools.logging :as log]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]))

(def instance
  (bus/event-bus))

(defn log-error!
  [e]
  (log/warn e "Error while listing to client events"))

(defn confirm!
  [& args]
  (d/success-deferred true))

(defn listen
  [subscribed? callback]
  (let [messages (bus/subscribe instance ::messages)]
    (s/consume-async
     (fn [[topic message]]
       (-> (if (subscribed? topic)
             (d/future (callback topic message))
             (d/success-deferred true))
           (d/catch log-error!)
           (d/chain confirm!)))
       messages)
    (fn [] (s/close! messages))))

(defn publish!
  [topic message]
  (bus/publish! instance ::messages [topic message]))

(defn bind
  [topic-set]
  (reify uib/Bindable
    (subscribe [_ handler]
      (listen topic-set (fn [topic message] (handler [topic message]))))
    (notify [_ message]
      (doseq [topic topic-set]
        (publish! topic message)))))

(defstate log
  :start (let [messages (bus/subscribe instance ::messages)]
           (s/consume-async #(log/trace %) messages)
           messages)
  :stop (s/close! log))
