(ns zdl-lex-server.status
  (:require [environ.core :refer [env]]
            [ring.util.http-response :as htstatus]))

(def anonymous-user (env :zdl-lex-anon-user "nobody"))

(defn handle-req [{:keys [zdl-lex-server.http/user]}]
  (htstatus/ok {:user (or user anonymous-user)}))


