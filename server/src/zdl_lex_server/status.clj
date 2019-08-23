(ns zdl-lex-server.status
  (:require [zdl-lex-server.env :refer [config]]
            [ring.util.http-response :as htstatus]))

(defn handle-req [{:keys [zdl-lex-server.http/user]}]
  (htstatus/ok {:user (or user (config :anon-user))}))


