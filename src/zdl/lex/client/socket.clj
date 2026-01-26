(ns zdl.lex.client.socket
  (:require
   [chime.core :as chime]
   [hato.websocket :as ws]
   [lambdaisland.uri :as uri]
   [zdl.lex.env :as env]
   [zdl.lex.client :as client]
   [zdl.lex.util :refer [pr-edn-str]]
   [taoensso.telemere :as tm]
   [tick.core :as t]))

(def ws-url
  (-> (uri/uri env/server-url)
      (update :scheme {"http" "ws" "https" "wss"})
      (uri/join "client")
      (str)))

(def ^:dynamic ws
  nil)

(defn close-socket!
  []
  (try
    (some-> ws (ws/close!) (deref))
    (catch Throwable t
      (tm/error! {:id ::close :data {:url ws-url}} t))
    (finally
      (alter-var-root #'ws (constantly nil)))))

(defn on-message
  [_ws ^java.nio.CharBuffer msg _last?]
  (tm/log! {:id    ::message
            :level :info
            :data  {:content (str msg)}}))

(defn on-error
  [_ws error]
  (tm/error! {:id ::error :data {:url ws-url}} error)
  (close-socket!))

(defn on-close
  [_ws status reason]
  (tm/log! {:id    ::closing
            :level :info
            :data  {:url    ws-url
                    :status status
                    :reason reason}}))

(def ws-opts
  {:on-message on-message
   :on-error   on-error
   :on-close   on-close})

(defn open-socket!
  []
  (when (and (nil? ws) (some? client/active-user))
    (->> (ws/websocket ws-url (assoc ws-opts :http-client @client/http-client))
         (deref)
         (constantly)
         (alter-var-root #'ws))))

(defn send!
  [data]
  (when ws
    (locking ws
      (try
        @(ws/send! ws (pr-edn-str data))
        (catch Throwable t
          (tm/error! {:id ::send :data data} t)
          (close-socket!))))))

(defn ping!
  []
  (send! @client/id))

(defn task-error-handler
  [e]
  (tm/error! {:id ::task-error} e)
  (not (instance? InterruptedException e)))

(defn schedule
  [interval-duration f]
  (-> (chime/periodic-seq (t/instant) interval-duration)
      (chime/chime-at f {:error-handler task-error-handler})))

(def ^:dynamic keep-alive
  nil)

(defn start
  []
  (->> (schedule (t/of-seconds 5) (fn [_] (open-socket!) (ping!)))
       (constantly)
       (alter-var-root #'keep-alive)))

(defn stop
  []
  (try
    (when keep-alive (.close keep-alive))
    (finally
      (close-socket!)
      (alter-var-root #'keep-alive (constantly nil)))))
