(ns zdl-lex-client.search
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.query :as query])
  (:import java.util.UUID))

(defonce query (atom ""))

(defonce responses (async/chan))

(defstate requests
  :start (let [ch (async/chan (async/sliding-buffer 3))]
           (async/go-loop []
             (when-let [req (async/<! ch)]
               (try
                 (let [q (query/translate (req :query))]
                   (->> (async/thread (http/search-articles q))
                        (async/<!)
                        (merge req)
                        (async/>! responses)))
                 (catch Exception e (timbre/warn e)))
               (recur)))
           ch)
  :stop (async/close! requests))

(defn request [q]
  (reset! query q)
  (async/>!! requests {:query q :id (str (UUID/randomUUID))}))
