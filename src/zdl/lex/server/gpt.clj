(ns zdl.lex.server.gpt
  (:require
   [clojure.core.async :as a]
   [integrant.core :as ig]
   [jsonista.core :as json]
   [taoensso.telemere :as tm])
  (:import
   (com.rabbitmq.client AMQP$BasicProperties$Builder CancelCallback ConnectionFactory DeliverCallback)))

(tm/set-min-level! nil "com.rabbitmq.client.TrustEverythingTrustManager" :error)

(defrecord Exchange [id messages timeout])

(defn ->exchange
  [messages & {:keys [timeout] :or {timeout 30000}}]
  (->Exchange (str (random-uuid)) messages timeout))

(def exchanges*
  (a/chan (a/sliding-buffer 1)))

(def exchanges
  (a/pub exchanges* :id))

(defn complete
  [{:keys [channel send reply] :as _queue} {:keys [id messages] :as _exchange}]
  (let [props   (.. (AMQP$BasicProperties$Builder.)
                    (correlationId id) (replyTo reply)
                    (build))
        payload (json/write-value-as-bytes messages)]
    (.basicPublish channel "" send props payload)))

(defn async-complete
  [queue {:keys [id timeout] :as exchange}]
  (a/go
    (let [ch (a/chan)]
      (try
        (a/sub exchanges id (a/chan))
        (a/thread (complete queue exchange))
        (a/alt! ch (a/timeout timeout))
        (finally
          (a/unsub exchanges id ch)
          (a/close! ch))))))

(defmethod ig/init-key ::queue
  [_ {:keys [host port user password queue] :as opts}]
  (tm/log! {:id ::connect :level :info :data opts})
  (let [conn-factory (doto (ConnectionFactory.)
                       (.setHost host)
                       (.setPort port)
                       (.setUsername user)
                       (.setPassword password)
                       (.useSslProtocol)
                       (.setConnectionTimeout 10000)
                       (.setHandshakeTimeout 10000)
                       (.setShutdownTimeout 10000))
        connection   (.newConnection conn-factory)
        channel      (.createChannel connection)
        reply        (.. channel (queueDeclare) (getQueue))
        state        {:connection connection
                      :channel    channel
                      :send       queue
                      :reply      reply}]
    (doto channel
      (.queueDeclare queue true false false nil)
      (.basicQos 1)
      (.basicConsume
       reply true
       (reify DeliverCallback
         (handle [_this _consumer-tag delivery]
           (let [id       (.. delivery (getProperties) (getCorrelationId))
                 response (json/read-value (.. delivery (getBody)))
                 exchange (->Exchange id response 0)]
             (tm/log! {:id    ::response
                       :level :debug
                       :data  {:exchange exchange}})
             (a/>!! exchanges* exchange))))
       (reify CancelCallback
         (handle [_this _consumer-tag]
           (tm/log! {:id    ::cancel
                     :level :error
                     :data  state})))))
    state))

(defmethod ig/halt-key! ::queue
  [_ {:keys [connection channel] :as state}]
  (tm/log! {:id ::disconnect :level :info :data state})
  (.close channel)
  (.close connection))
