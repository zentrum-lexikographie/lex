(ns zdl-lex-server.status
  (:require [zdl-lex-server.env :refer [config]]
            [ring.util.http-response :as htstatus]))

(defn user [{:keys [headers]}]
  (get headers "x-remote-user" (config :anon-user)))

(defn handle [req]
  (htstatus/ok {:user (user req)}))


