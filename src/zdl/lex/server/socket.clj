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
  [req response]
  (let [data {:request req :response response}]
    (if-let [ch (@sockets (message->socket-key req))]
      (ws/send ch (pr-edn-str response)
               (fn [] (tm/log! {:id ::reply-success :level :info :data data}))
               (fn [e] (tm/error! {:id ::reply-failure :data data} e)))
      (tm/log! {:id ::reply-discarded :level :info :data data}))))


(defmulti handle-request
  (fn [_gpt_queue {:keys [content-type]}] content-type))

(defmethod handle-request :gpt [gpt-queue {{:keys [id messages]} :content :as req}]
  (a/go
    (let [gpt-exchange (gpt/->exchange messages)
          completion   (a/<! (gpt/async-complete gpt-queue gpt-exchange))
          gpt-reply    (merge {:id id} (or completion {:error :timeout}))]
      (reply req {:content-type :gpt :content gpt-reply}))))


(defmethod handle-request :default [_gpt-queue req]
  (tm/log! {:id ::request :level :info :data {:request req}}))

(defn on-message
  [gpt-queue user ch message]
  (let [message     (assoc (read-string message) :user user)
        fingerprint (dissoc message :content-type :content)]
    (swap! clients assoc ch fingerprint)
    (swap! sockets assoc-in (message->socket-key message) ch)
    (handle-request gpt-queue message)))

(defn on-close
  [ch _status _reason]
  (let [fingerprint (@clients ch)]
    (swap! clients dissoc ch)
    (swap! sockets dissoc-in (message->socket-key fingerprint))))

(defn handle-client
  [gpt-queue {:keys [websocket?] {:keys [user]} :identity :as _req}]
  (if websocket?
    {::ws/listener {:on-message (partial on-message gpt-queue user)
                    :on-close   on-close}}
    (resp/status 400)))

(defn handlers
  [gpt-queue]
  ["" (partial handle-client gpt-queue)])
