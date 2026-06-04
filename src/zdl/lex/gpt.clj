(ns zdl.lex.gpt
  (:require
   [clojure.string :as str]
   [integrant.core :as ig]
   [jsonista.core :as json]
   [org.httpkit.client :as hc]
   [taoensso.telemere :as tel]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.metrics :as metrics]
   [zdl.lex.queue :as queue])
  (:import
   (com.rabbitmq.client AMQP$BasicProperties$Builder Channel Delivery)
   (org.httpkit.client ClientSslEngineFactory)))

(def gpt-api-url
  (getenv "GPT_API_URL" "https://zdl-gpu01.bbaw.de/api/"))

(def gpt-api-key
  (getenv "GPT_API_KEY" "abc123"))

(def gpt-model
  (getenv "MODEL" "llama33"))

(def error-meter
  (metrics/meter "gpt.errors"))

(def completion-timer
  (metrics/timer "gpt.completion"))

(defn log-api-request
  [{:keys [error] :as response}]
  (tel/with-ctx+ {::gpt-api-response response}
    (if-not error
      (do (tel/event! ::gpt-api-tx :debug) response)
      (do (tel/error! ::gpt-api-error error) nil))))

(defn read-json-response
  [{{:keys [content-type]} :headers :as response}]
  (when response
    (cond-> response
      (str/includes? "application/json" (or content-type ""))
      (update :body json/read-value))))

(def default-callback
  (comp read-json-response log-api-request))

(defn gpt-api-request
  ([req]
   (gpt-api-request req default-callback))
  ([req f]
   (tel/with-ctx+ {::gpt-api-request req}
     (-> {:sslengine (ClientSslEngineFactory/trustAnybody) :keepalive -1}
         (merge req)
         (update :url (partial str gpt-api-url))
         (assoc-in [:headers "Authorization"] (str "Bearer " gpt-api-key))
         (hc/request #(tel/with-ctx+ {::gpt-api-request req} (f %)))))))

(defn models
  []
  (-> (gpt-api-request {:method :get :url "models"}) deref :body (get "data")))

(defn complete
  ([req]
   (complete req default-callback))
  ([req f]
  (-> {:method  :post
       :url     "chat/completions"
       :headers {"Content-Type" "application/json"}
       :body    (json/write-value-as-string (assoc req "model" gpt-model))}
      (gpt-api-request f))))

(defn qa
  [q]
  (->
   (complete {"messages" [{"role" "user" "content" q}]})
   deref (get-in [:body "choices" 0 "message" "content"])))

(defn complete-on-request
  [^Channel channel ^Delivery delivery]
  (let [delivery-tag   (.. delivery (getEnvelope) (getDeliveryTag))
        ack!           (fn [] (.. channel (basicAck delivery-tag false)))
        delivery-props (.. delivery (getProperties))
        correlation-id (.. delivery-props (getCorrelationId))
        reply-to       (.. delivery-props (getReplyTo))
        reply-props    (.. (AMQP$BasicProperties$Builder.)
                           (correlationId correlation-id)
                           (build))
        completion-time (metrics/timed! completion-timer)]
    (try
      (let [req (json/read-value (.. delivery (getBody)))]
        (tel/with-ctx+ {::request req}
          (tel/event! ::request :debug)
          (complete
           req
           (fn [{:keys [error body] :as resp}]
             (try
               (.close completion-time)
               (tel/with-ctx+ {::request req ::response resp}
                 (tel/event! ::response :debug)
                 (cond
                   error (do (metrics/metered! error-meter)
                             (tel/error! ::completion-error error))
                   body  (.basicPublish channel "" reply-to reply-props
                                        (.getBytes ^String body "UTF-8"))))
               (finally
                 (ack!)))))))
      (catch Throwable t
        (tel/error! ::request t)
        (ack!)))))

(defn echo-on-request
  [^Channel channel ^Delivery delivery]
  (let [delivery-tag    (.. delivery (getEnvelope) (getDeliveryTag))
        ack!            (fn [] (.. channel (basicAck delivery-tag false)))
        delivery-props  (.. delivery (getProperties))
        correlation-id  (.. delivery-props (getCorrelationId))
        reply-to        (.. delivery-props (getReplyTo))
        reply-props     (.. (AMQP$BasicProperties$Builder.)
                           (correlationId correlation-id)
                           (build))
        completion-time (metrics/timed! completion-timer)]
    (try
      (let [req  (json/read-value (.. delivery (getBody)))
            resp req]
        (tel/with-ctx+ {::request req ::response resp}
          (tel/event! ::echo :debug)
          (.close completion-time)
          (.basicPublish channel "" reply-to reply-props
                         (json/write-value-as-bytes resp))))
      (catch Throwable t
        (tel/error! ::echo t))
      (finally
        (ack!)))))

(defn log-on-cancel
  [_channel]
  (tel/event! ::canceled :error))

(defn exit-on-cancel
  [& _]
  (System/exit 1))

(def dev-config
  {::queue/connection     {}
   ::queue/gpt-rpc-server {:queue      (ig/ref ::queue/connection)
                           :on-request echo-on-request
                           :on-cancel  log-on-cancel}})

(def main-config
  {::metrics/reporter     {}
   ::queue/connection     {}
   ::queue/gpt-rpc-server {:queue      (ig/ref ::queue/connection)
                           :on-request complete-on-request
                           :on-cancel  (comp exit-on-cancel log-on-cancel)}})

(defn -main
  [& _]
  (let [system (ig/init main-config)]
    (. (Runtime/getRuntime) (addShutdownHook (Thread. #(ig/halt! system))))
    @(promise)))
