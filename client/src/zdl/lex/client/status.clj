(ns zdl.lex.client.status
  (:require [manifold.deferred :as d]
            [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [zdl.lex.client :as client]
            [zdl.lex.client.auth :as auth]
            [zdl.lex.client.bus :as bus]
            [zdl.lex.cron :as cron]
            [seesaw.bind :as uib])
  (:import java.time.Instant))

(defn ping!
  []
  (d/chain
   (auth/with-authentication (client/get-status))
   (fn [{status :body}]
     (bus/publish! :status (assoc status :timestamp (Instant/now))))))

(defstate ping
  :start (cron/schedule-stream "*/30 * * * * ?" "Ping Status" ping!)
  :stop (s/close! ping))

(defn trigger!
  []
  (s/put! ping "<Ping>"))

(def current-user
  (atom nil))

(defn status->user
  [[_ {:keys [user]}]]
  user)

(defstate status->current-user
  :start (uib/bind (bus/bind #{:status})
                   (uib/transform status->user)
                   current-user)
  :stop (status->current-user))

