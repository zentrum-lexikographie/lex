(ns zdl-lex-client.http
  (:require [clojure.java.io :as io]
            [clojure.data.codec.base64 :as base64]
            [zdl-lex-client.env :refer [config]])
  (:import [java.io IOException]
           [java.net URL ConnectException]))

(def server-base (config :server-base))

(def ^{:private true} basic-creds
  (let [{:keys [user password]} (config :server-auth)]
    (if (and user password)
      (apply str (map char (base64/encode (.getBytes (str user ":" password))))))))

(defn- tx [path f]
  (let [con (-> (str server-base path) (URL.) (.openConnection))]
    (try
      (when basic-creds
        (.setRequestProperty con "Authorization" (str "Basic " basic-creds)))
      (f con)
      (catch ConnectException e
        (throw (ex-info "I/O error while connecting to XML database" {} e)))
      (catch IOException e
        (with-open [err (io/reader (.getErrorStream con) :encoding "UTF-8")]
          (throw (ex-info "I/O error while talking to XML database"
                          {:http-status (.getResponseCode con)
                           :http-message (.getResponseMessage con)
                           :http-body (slurp err)})))))))
