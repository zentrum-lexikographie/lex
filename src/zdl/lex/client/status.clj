(ns zdl.lex.client.status
  (:require [clojure.core.async :as a]
            [manifold.deferred :as d]
            [mount.core :refer [defstate]]
            [zdl.lex.client :as client]
            [zdl.lex.client.auth :as auth]
            [zdl.lex.client.bus :as bus]
            [zdl.lex.cron :as cron])
  (:import java.time.Instant))

(defn ping!
  []
  (d/chain
   (auth/with-authentication (client/get-status))
   (fn [{status :body}]
     (bus/publish! :status (assoc status :timestamp (Instant/now))))))

(defstate ping
  :start (cron/schedule "*/30 * * * * ?" "Ping Status" ping!)
  :stop (a/close! ping))

(defn trigger!
  []
  (a/>!! ping "<Ping>"))

(def current-user
  (atom nil))

(defstate status->current-user
  :start (bus/listen #{:status} #(reset! current-user (get %2 :user)))
  :stop (status->current-user))

