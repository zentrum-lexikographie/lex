(ns zdl.lex.client.socket
  (:require
   [chime.core :as chime]
   [hato.websocket :as ws]
   [integrant.core :as ig]
   [lambdaisland.uri :as uri]
   [zdl.lex.util :refer [pr-edn-str]]
   [taoensso.telemere :as tm]
   [tick.core :as t]))

(def ws
  (atom nil))

(defn close-socket!
  []
  (try
    (some-> @ws (ws/close!) (deref))
    (catch Throwable t
      (tm/error! {:id ::close} t))
    (finally
      (reset! ws nil))))

(defn on-message
  [_ws ^java.nio.CharBuffer msg _last?]
  (tm/log! {:id ::message :level :info :data {:content (str msg)}}))

(defn on-error
  [_ws error]
  (tm/error! {:id ::error} error)
  (close-socket!))

(defn on-close
  [_ws status reason]
  (tm/log! {:id ::closing :level :info :data {:status status :reason reason}}))

(def ws-opts
  {:on-message on-message
   :on-error   on-error
   :on-close   on-close})

(defn open-socket!
  [ws-url http-client]
  (or @ws (reset! ws @(ws/websocket ws-url {:on-message  on-message
                                            :on-error    on-error
                                            :on-close    on-close
                                            :http-client http-client}))))

(defn send!
  [data]
  (when-let [ws @ws]
    (locking ws
      (try
        @(ws/send! ws (pr-edn-str data))
        (catch Throwable t
          (tm/error! {:id ::send :data data} t)
          (close-socket!))))))

(defmethod ig/init-key ::connection
  [_ {:keys [server-url http-client active-user]}]
  (let [ws-url (-> (uri/uri server-url)
                   (update :scheme {"http" "ws" "https" "wss"})
                   (uri/join "socket")
                   (str))]
    (chime/chime-at
     (chime/periodic-seq (t/instant) (t/of-seconds 5))
     (fn [_] (when @active-user (open-socket! ws-url @http-client)))
     {:error-handler (fn [e]
                       (tm/error! {:id ::task-error} e)
                       (not (instance? InterruptedException e)))})))

(defmethod ig/halt-key! ::connection
  [_ connection]
  (.close connection)
  (close-socket!))
