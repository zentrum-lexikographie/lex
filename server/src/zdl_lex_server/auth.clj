(ns zdl-lex-server.auth
  (:require [clojure.data.codec.base64 :as base64]
            [clojure.string :as str]
            [zdl-lex-common.env :refer [env]]))

(let [anonymous-user (env :http-anon-user)
      decode #(-> (.getBytes ^String % "UTF-8") (base64/decode) (String.))]
  (defn assoc-auth [{:keys [headers] :as request}]
    (let [auth (some-> (get-in request [:headers "authorization"])
                       (str/replace #"^Basic " "") (decode) (str/split #":" 2))]
      (assoc request
             ::user (or (first auth) anonymous-user)
             ::password (second auth)))))

(defn wrap-auth
  [handler]
  (fn
    ([request]
     (handler (assoc-auth request)))
    ([request respond raise]
     (handler (assoc-auth request) respond raise))))

