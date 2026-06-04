(ns zdl.lex.queue
  (:require
   [clojure.core.async :as a]
   [integrant.core :as ig]
   [jsonista.core :as json]
   [taoensso.telemere :as tel]
   [zdl.lex.env :refer [getenv]])
  (:import
   (com.rabbitmq.client AMQP$BasicProperties$Builder CancelCallback ConnectionFactory DeliverCallback)))

(tel/set-min-level! nil "com.rabbitmq.client.TrustEverythingTrustManager" :error)

(def rpc-queues
  ["gpt" "nlp"])

(def spec
  {:host     (getenv "QUEUE_HOST" "localhost")
   :port     (parse-long (getenv "QUEUE_PORT" "5671"))
   :user     (getenv "QUEUE_USER" "lex")
   :password (getenv "QUEUE_PASSWORD" "lex")})

(def connection-factory
  (doto (ConnectionFactory.)
    (.setHost (spec :host))
    (.setPort (spec :port))
    (.setUsername (spec :user))
    (.setPassword (spec :password))
    (.useSslProtocol)
    (.setConnectionTimeout 10000)
    (.setHandshakeTimeout 10000)
    (.setShutdownTimeout 10000)))

(def messages
  (a/chan))

(def broadcast
  (a/pub messages (juxt :queue :id)))

(defn rpc-request
  [{:keys [channel rpc-responses] :as _client} queue id message]
  (tel/with-ctx+ {::message {:queue queue :id id :message message}}
    (tel/event! ::rpc-request :debug)
    (.basicPublish channel "" queue
                   (.. (AMQP$BasicProperties$Builder.)
                       (correlationId id)
                       (replyTo (rpc-responses queue))
                       (build))
                   (json/write-value-as-bytes message))))

(defn rpc
  ([client queue message]
   (rpc client queue message 30000))
  ([client queue message timeout]
   (let [id (str (random-uuid))]
     (a/go
       (let [ch (a/chan)]
         (try
           (a/sub broadcast [queue id] ch)
           (a/io-thread (rpc-request client queue id message))
           (a/alt! [ch (a/timeout timeout)] ([v _ch] v))
           (finally
             (a/unsub broadcast [queue id] ch)
             (a/close! ch))))))))

(defmethod ig/init-key ::connection
  [_ _]
  (tel/with-ctx+ {::spec spec}
    (tel/event! ::connect)
    (let [connection (.newConnection connection-factory)
          channel    (.createChannel connection)]
      {:connection connection :channel channel})))

(defmethod ig/halt-key! ::connection
  [_ {:keys [connection channel]}]
  (tel/with-ctx+ {::spec spec}
    (tel/event! ::disconnect)
    (.close channel)
    (.close connection)))

(defmethod ig/init-key ::rpc-client
  [_  {{:keys [channel] :as queue} :queue}]
  (let [responses (transient {})]
    (doseq [q rpc-queues :let [r (.. channel (queueDeclare) (getQueue))]]
      (tel/with-ctx+ {::queue queue}
        (doto channel
          (.queueDeclare q true false false nil)
          (.basicQos 1)
          (.basicConsume
           r true
           (reify DeliverCallback
             (handle [_this _consumer-tag delivery]
               (let [message {:queue   q
                              :id      (.. delivery
                                           (getProperties)
                                           (getCorrelationId))
                              :message (json/read-value
                                        (.. delivery (getBody)))}]
                 (tel/with-ctx+ {::message message}
                   (tel/event! ::rpc-response :debug)
                   (a/>!! messages message)))))
           (reify CancelCallback
             (handle [_this _consumer-tag] (tel/error! ::cancel)))))
        (assoc! responses q r)))
    (assoc queue :rpc-responses (persistent! responses))))

(defn rpc-server
  [rpc-queue {:keys [on-request on-cancel] {:keys [channel]} :queue}]
  (doto channel
    (.queueDeclare rpc-queue true false false nil)
    (.basicQos 1)
    (.basicConsume
     rpc-queue false
     (reify DeliverCallback
       (handle [_this _consumer-tag delivery] (on-request channel delivery)))
     (reify CancelCallback
       (handle [_this _consumer-tag] (on-cancel channel))))))


(defmethod ig/init-key ::gpt-rpc-server
  [_ opts]
  (rpc-server "gpt" opts))

(defmethod ig/init-key ::nlp-rpc-server
  [_ opts]
  (rpc-server "nlp" opts))
