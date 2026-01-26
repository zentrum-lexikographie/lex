(ns zdl.lex.server.socket
  (:require
   [medley.core :refer [dissoc-in]]
   [ring.websocket :as ws]
   [ring.util.response :as resp]
   [taoensso.telemere :as tm]
   [zdl.lex.server.gpt :as gpt]
   [zdl.lex.util :refer [pr-edn-str]]
   [clojure.core.async :as a]))

(def sockets
  (atom {}))

(def clients
  (atom {}))

(defn message->socket-key
  [{:keys [user client-id]}]
  [user client-id])

(defn reply
  [message reply]
  (if-let [ch (@sockets (message->socket-key message))]
    (ws/send ch (pr-edn-str reply)
             (fn [] (tm/log! {:id    ::reply-success
                              :level :info
                              :data  {:message message
                                      :reply   reply}}))
             (fn [e] (tm/error! {:id ::reply-failure
                                 :data {:message message
                                        :reply   reply}}
                                e)))
    (tm/log! {:id    ::reply-discarded
              :level :info
              :data  {:message message
                      :reply   reply}})))


(defmulti handle-message :content-type)

(defmethod handle-message :gpt [{{:keys [id messages]} :content :as message}]
  (a/go
    (let [gpt-exchange (gpt/->Exchange (str (random-uuid)) messages)
          completion   (a/<! (gpt/async-complete gpt-exchange))]
      (reply message {:content-type :gpt
                      :content      (merge completion {:id id})}))))


(defmethod handle-message :default [message]
  (tm/log! {:id    ::message
            :level :info
            :data  {:content message}}))

(defn on-message
  [user ch message]
  (let [message     (assoc (read-string message) :user user)
        fingerprint (dissoc message :content-type :content)]
    (swap! clients assoc ch fingerprint)
    (swap! sockets assoc-in (message->socket-key message) ch)
    (handle-message message)))

(defn on-close
  [ch _status _reason]
  (let [fingerprint (@clients ch)]
    (swap! clients dissoc ch)
    (swap! sockets dissoc-in (message->socket-key fingerprint))))

(defn handle-client
  [{:keys [websocket?] {:keys [user]} :identity :as _req}]
  (if websocket?
    {::ws/listener {:on-message (partial on-message user) :on-close on-close}}
    (resp/status 400)))
