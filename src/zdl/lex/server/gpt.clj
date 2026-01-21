(ns zdl.lex.server.gpt
  (:require
   [zdl.lex.env :as env]
   [jsonista.core :as json]
   [taoensso.telemere :as tm]
   [clojure.core.async :as a])
  (:import
   (com.rabbitmq.client AMQP$BasicProperties$Builder CancelCallback DeliverCallback)))

(defrecord Exchange [id messages])

(def exchanges*
  (a/chan (a/sliding-buffer 1)))

(def exchanges
  (a/mult exchanges*))

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
              :data  {:connection-factory env/queue-connection-factory
                      :queue-name         env/gpt-queue-name}})
    (let [mq-connection*  (.newConnection env/queue-connection-factory)
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
                   exchange (->Exchange id response)]
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
