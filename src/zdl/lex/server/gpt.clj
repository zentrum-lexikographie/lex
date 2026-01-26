(ns zdl.lex.server.gpt
  (:require
   [zdl.lex.env :as env]
   [jsonista.core :as json]
   [taoensso.telemere :as tm]
   [clojure.core.async :as a])
  (:import
   (com.rabbitmq.client AMQP$BasicProperties$Builder CancelCallback ConnectionFactory DeliverCallback)))

(defrecord Exchange [id messages timeout])

(defn ->exchange
  [messages & {:keys [timeout] :or {timeout 30000}}]
  (->Exchange (str (random-uuid)) messages timeout))

(def exchanges*
  (a/chan (a/sliding-buffer 1)))

(def exchanges
  (a/pub exchanges* :id))

(def ^:dynamic mq-connection
  nil)

(def ^:dynamic mq-channel
  nil)

(def ^:dynamic mq-reply-queue
  nil)

(defn complete
  [{:keys [id messages] :as _exchange}]
  (when (and mq-channel mq-reply-queue)
    (.basicPublish
     mq-channel
     ""
     env/gpt-queue-name
     (.. (AMQP$BasicProperties$Builder.)
         (correlationId id)
         (replyTo mq-reply-queue)
         (build))
     (json/write-value-as-bytes messages))))

(def completion-timeout
  30000)

(defn async-complete
  [{:keys [id timeout] :as exchange}]
  (a/go
    (let [ch (a/chan)]
      (try
        (a/sub exchanges id (a/chan))
        (a/thread (complete exchange))
        (a/alt! ch (a/timeout timeout))
        (finally
          (a/unsub exchanges id ch)
          (a/close! ch))))))

(defn disconnect
  []
  (try
    (tm/log! {:id    ::disconnect
              :level :debug
              :data  {:reply-queue mq-reply-queue
                      :channel     mq-channel
                      :connection  mq-connection}})
    (when mq-channel (.close mq-channel))
    (when mq-connection (.close mq-connection))
    (catch Throwable t
      (tm/error! {:id   ::disconnect
                  :data {:reply-queue mq-reply-queue
                         :channel     mq-channel
                         :connection  mq-connection}}
                 t))
    (finally
      (alter-var-root #'mq-channel (constantly nil))
      (alter-var-root #'mq-connection (constantly nil)))))

(defn connect
  []
  (try
    (tm/log! {:id    ::start
              :level :info
              :data  {:connection env/queue
                      :queue-name env/gpt-queue-name}})
    (let [mq-conn-factory (doto (ConnectionFactory.)
                           (.setHost (env/queue :host))
                           (.setPort (env/queue :port))
                           (.setUsername (env/queue :user))
                           (.setPassword (env/queue :password))
                           (.useSslProtocol)
                           (.setConnectionTimeout 10000)
                           (.setHandshakeTimeout 10000)
                           (.setShutdownTimeout 10000))
          mq-connection*  (.newConnection mq-conn-factory)
          mq-channel*     (.createChannel mq-connection*)
          mq-reply-queue* (.. mq-channel* (queueDeclare) (getQueue))]
      (alter-var-root #'mq-connection (constantly mq-connection*))
      (alter-var-root #'mq-channel (constantly mq-channel*))
      (alter-var-root #'mq-reply-queue (constantly mq-reply-queue*))
      (doto mq-channel
        (.queueDeclare env/gpt-queue-name true false false nil)
        (.basicQos 1)
        (.basicConsume
         mq-reply-queue true
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
                       :data  {:reply-queue mq-reply-queue
                               :channel     mq-channel
                               :connection  mq-connection}}))))))
    (catch Throwable t
      ;; TODO: make connection errors fatal upon app start
      (tm/error! {:id ::start} t))))
