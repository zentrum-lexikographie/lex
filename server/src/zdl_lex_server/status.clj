(ns zdl-lex-server.status
  (:require [ring.util.http-response :as htstatus]))

(def ring-handlers
  ["/status" {:get (fn [{:keys [zdl-lex-server.http/user]}]
                     (htstatus/ok {:user user}))}])

