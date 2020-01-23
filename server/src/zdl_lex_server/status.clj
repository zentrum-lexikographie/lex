(ns zdl-lex-server.status
  (:require [ring.util.http-response :as htstatus]))

(defn get-status
  [{:keys [identity]}]
  (htstatus/ok identity))

(def ring-handlers
  ["/status"
   {:get {:summary "Provides status information, e.g. logged-in user"
          :tags ["Status"]
          :handler get-status}}])

