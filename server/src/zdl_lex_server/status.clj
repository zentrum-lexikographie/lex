(ns zdl-lex-server.status
  (:require [ring.util.http-response :as htstatus]
            [zdl-lex-server.auth :refer [wrap-authenticated]]))

(defn get-status
  [{:keys [basic-authentication]}]
  (htstatus/ok {:user (first basic-authentication)
                :password (second basic-authentication)}))

(def ring-handlers
  ["/status"
   {:get {:summary "Provides status information, e.g. logged-in user"
          :tags ["Status"]
          :handler get-status
          :middleware [wrap-authenticated]}}])

