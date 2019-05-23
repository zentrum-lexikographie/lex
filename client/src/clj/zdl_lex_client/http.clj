(ns zdl-lex-client.http
  (:require [cemerick.url :refer [url]]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [zdl-lex-client.env :refer [config]])
  (:import java.io.IOException
           [java.net ConnectException URL]))

(def server-base (config :server-base))

(def ^:private basic-creds
  (let [{:keys [user password]} (config :server-auth)]
    (if (and user password)
      (apply str (map char (base64/encode (.getBytes (str user ":" password))))))))

(defn tx [rf uf]
  (let [con (-> server-base url uf str (URL.) (.openConnection))]
    (try
      (when basic-creds
        (.setRequestProperty con "Authorization" (str "Basic " basic-creds)))
      (rf con)
      (catch ConnectException e
        (throw (ex-info "I/O error while connecting to XML database" {} e)))
      (catch IOException e
        (with-open [err (io/reader (.getErrorStream con) :encoding "UTF-8")]
          (throw (ex-info "I/O error while talking to server"
                          {:http-status (.getResponseCode con)
                           :http-message (.getResponseMessage con)
                           :http-body (slurp err)})))))))

(def get-edn
  (partial tx (fn [con]
                (.setRequestProperty con "Accept" "application/edn")
                (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
                  (-> resp slurp read-string)))))
