(ns zdl.lex.client.socket
  (:require
   [hato.websocket :as ws]
   [lambdaisland.uri :as uri]
   [zdl.lex.env :as env]
   [zdl.lex.client :as client]
   [taoensso.telemere :as tm]))

(def ws-url
  (-> (uri/uri env/server-url)
      (update :scheme {"http" "ws" "https" "wss"})
      (uri/join "client")
      (str)))

(def ^:dynamic ws
  nil)


(defn close!
  []
  (try
    (some-> ws (ws/close!) (deref))
    (catch Throwable t
      (tm/error! {:id ::close :data {:url ws-url}} t))
    (finally
      (alter-var-root #'ws (constantly nil)))))

(defn on-message
  [_ws msg _last?]
  (tm/log! {:id    ::message
            :level :info
            :data  {:content msg}}))

(defn on-error
  [_ws error]
  (tm/error! {:id ::error :data {:url ws-url}} error)
  (close!))

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

(defn open!
  []
  (when-not ws
    (->> (ws/websocket ws-url (assoc ws-opts :http-client @client/http-client))
         (deref)
         (constantly)
         (alter-var-root #'ws))))
