(ns zdl-lex-server.status
  (:require [ring.util.http-response :as htstatus]
            [zdl-lex-server.auth :as auth]))

(def ring-handlers
  ["/status" {:get {:summary "Provides status information, e.g. logged-in user"
                    :tags ["Status"]
                    :handler (fn [{:keys [auth/user]}]
                               (htstatus/ok {:user user}))}}])

