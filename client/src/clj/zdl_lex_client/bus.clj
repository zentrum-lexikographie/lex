(ns zdl-lex-client.bus
  (:require [manifold.bus :as b]
            [manifold.stream :as s]
            [seesaw.bind :as uib]
            [manifold.deferred :as d]))

(defonce ^:private instance (b/event-bus))

(def publish! (partial b/publish! instance))

(def subscribe (partial b/subscribe instance))

(defn bind [topic]
  (reify uib/Bindable
    (subscribe [_ handler]
      (let [subscription (subscribe topic)
            subscribed? (atom true)]
        (->> subscription
             (s/consume-async
              (fn [v]
                (if @subscribed?
                  (do (handler v) (d/success-deferred true))
                  (do (s/close! subscription) (d/success-deferred false))))))
        (fn [] (reset! subscribed? false))))
    (notify [_ v]
      (publish! topic v))))



