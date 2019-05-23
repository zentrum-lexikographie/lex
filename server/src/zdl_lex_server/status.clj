(ns zdl-lex-server.status
  (:require [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.article :as article]
            [zdl-lex-server.solr :as solr]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]))

(defn user [{:keys [headers]}]
  (get headers "x-remote-user" (config :anon-user)))

(defn handle [req]
  (htstatus/ok {:user (user req)}))


