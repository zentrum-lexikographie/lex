(ns zdl.lex.queue
  (:require
   [clojure.core.async :as a]
   [integrant.core :as ig]
   [jsonista.core :as json]
   [taoensso.telemere :as tel]
   [zdl.lex.env :refer [getenv]])
  (:import
   (com.rabbitmq.client AMQP$BasicProperties$Builder CancelCallback Channel Connection ConnectionFactory DeliverCallback)))

(tel/set-min-level! nil "com.rabbitmq.client.TrustEverythingTrustManager" :error)

(def spec
  {:host     (getenv "QUEUE_HOST" "localhost")
   :port     (parse-long (getenv "QUEUE_PORT" "5671"))
   :user     (getenv "QUEUE_USER" "lex")
   :password (getenv "QUEUE_PASSWORD" "lex")})

(def ^ConnectionFactory connection-factory
  (doto (ConnectionFactory.)
    (.setHost (spec :host))
    (.setPort (spec :port))
    (.setUsername (spec :user))
    (.setPassword (spec :password))
    (.useSslProtocol)
    (.setConnectionTimeout 10000)
    (.setHandshakeTimeout 10000)
    (.setShutdownTimeout 10000)))

(def ^:dynamic ^Connection connection
  nil)

(def ^:dynamic ^Channel channel
  nil)

(defn connect
  []
  (when-not (and connection channel)
    (tel/with-ctx+ {::spec spec}
      (tel/event! ::connect)
      (alter-var-root #'connection (constantly (.newConnection connection-factory)))
      (alter-var-root #'channel (constantly (.createChannel connection))))))

(defn disconnect
  []
  (when (and connection channel)
    (tel/with-ctx+ {::spec spec}
      (tel/event! ::disconnect)
      (try
        (.close channel)
        (.close connection)
        (finally
          (alter-var-root #'channel (constantly nil))
          (alter-var-root #'connection (constantly nil)))))))

(def messages
  (a/chan))

(def broadcast
  (a/pub messages (juxt :id :queue)))

(def rpc-queues
  #{"gpt" "nlp"})

(def ^:dynamic rpc-responses
  nil)

(defn rpc
  ([queue message]
   (rpc queue message 30000))
  ([queue message timeout]
   (let [id (str (random-uuid))]
     (a/go
       (let [ch (a/chan)]
         (try
           (a/sub broadcast [id queue] ch)
           (a/io-thread
            (tel/with-ctx+ {::message {:id id :queue queue :message message}}
              (tel/event! ::rpc-request :debug)
              (.basicPublish channel "" queue
                             (.. (AMQP$BasicProperties$Builder.)
                                 (correlationId id)
                                 (replyTo (rpc-responses queue))
                                 (build))
                             (json/write-value-as-bytes message))))
           (a/alt! [ch (a/timeout timeout)] ([v _ch] v))
           (finally
             (a/unsub broadcast id ch)
             (a/close! ch))))))))

(defn rpc-subscribe
  [queue]
  (let [responses (.. channel (queueDeclare) (getQueue))]
    (doto channel
      (.queueDeclare queue true false false nil)
      (.basicQos 1)
      (.basicConsume
       responses true
       (reify DeliverCallback
         (handle [_this _consumer-tag delivery]
           (let [id      (.. delivery (getProperties) (getCorrelationId))
                 body    (json/read-value (.. delivery (getBody)))
                 message {:id id :queue queue :message body}]
             (tel/with-ctx+ {::message message}
               (tel/event! ::rpc-response :debug)
               (a/>!! messages message)))))
       (reify CancelCallback
         (handle [_this _consumer-tag] (tel/error! ::cancel)))))
    [queue responses]))

(defmethod ig/init-key ::rpc-client
  [_  _]
  (when-not rpc-responses
    (connect)
    (->> (into {} (map rpc-subscribe) rpc-queues)
         (constantly) (alter-var-root #'rpc-responses))))

(defmethod ig/halt-key! ::rpc-client
  [_ _]
  (when rpc-responses
    (disconnect)
    (alter-var-root #'rpc-responses (constantly nil))))

(defmethod ig/init-key ::rpc-server
  [_ {:keys [on-cancel] rpc-handle :handle queue-name :queue :or {on-cancel (fn [])}}]
  (tel/with-ctx+ {::queue queue-name}
    (connect)
    (doto ^Channel channel
      (.queueDeclare queue-name true false false nil)
      (.basicQos 1)
      (.basicConsume
       ^String queue-name false
       (reify DeliverCallback
         (handle [_this _consumer-tag delivery]
           (let [delivery-tag   (.. delivery (getEnvelope) (getDeliveryTag))
                 ack!           (fn [] (.. channel (basicAck delivery-tag false)))
                 delivery-props (.. delivery (getProperties))
                 correlation-id (.. delivery-props (getCorrelationId))
                 reply-to       (.. delivery-props (getReplyTo))
                 reply-props    (.. (AMQP$BasicProperties$Builder.)
                                     (correlationId correlation-id)
                                     (build))
                 req            (.. delivery (getBody))]
             (try
               (.basicPublish channel "" reply-to reply-props (rpc-handle req))
               (catch Throwable t (tel/error! ::rpc-handle t))
               (finally (ack!))))))
       (reify CancelCallback
         (handle [_this _consumer-tag]
           (tel/event! ::cancel)
           (on-cancel)))))))

(defmethod ig/halt-key! ::rpc-server
  [_ _]
  (disconnect))
