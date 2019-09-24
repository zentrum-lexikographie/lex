(ns zdl-lex-server.status
  (:require [ring.util.http-response :as htstatus]))

(defn handle-req [{:keys [zdl-lex-server.http/user]}]
  (htstatus/ok {:user user}))


