(ns zdl.lex.server.socket
  (:require [ring.websocket :as ws]
            [ring.util.response :as resp]
            [taoensso.telemere :as tm]))

(defn on-open
  [_ch])

(defn on-message
  [ch message]
  (tm/log! {:id    ::message
            :level :info
            :data  {:message message}})
  (ws/send ch message))

(defn on-close
  [_ch _status _reason])

(defn listener
  [_req]
  {:on-open    on-open
   :on-message on-message
   :on-close   on-close})

(defn handle-client
  [{:keys [websocket?] :as req}]
  (if websocket? {::ws/listener (listener req)} (resp/status 400)))
