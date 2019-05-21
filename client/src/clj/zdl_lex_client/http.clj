(ns zdl-lex-client.http
  (:require [clojure.java.io :as io]
            [clojure.data.codec.base64 :as base64]
            [cemerick.url :refer [url]]
            [mount.core :refer [defstate]]
            [zdl-lex-client.env :refer [config]]
            [clojure.core.async :as async]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.bus :as bus])
  (:import [java.io IOException]
           [java.net URL ConnectException]))

(def server-base (config :server-base))

(def ^{:private true} basic-creds
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

(defn- request-edn [con]
  (.setRequestProperty con "Accept" "application/edn")
  (with-open [resp (io/reader (.getInputStream con) :encoding "UTF-8")]
    (-> resp slurp read-string)))

(def get-edn (partial tx request-edn))

(defn form-suggestions [q]
  (get-edn #(merge % {:path "/articles/forms/suggestions" :query {"q" q}})))

(defstate status-requests
  :start (let [stop-ch (async/chan)]
           (async/go-loop []
             (when (async/alt! stop-ch nil (async/timeout 10000) :tick)
               (try
                 (let [status (async/thread (get-edn #(merge % {:path "/status"})))
                       status (merge {:timestamp (t/now)} status)]
                   (reset! bus/status status))
                 (catch Exception e (timbre/warn e)))
                 (recur)))
           stop-ch)
  :stop (async/close! status-requests))

(defstate search-results
  :start (let [stop-ch (async/chan)]
           (async/go-loop []
             (when-let [q (async/alt! stop-ch nil bus/search-reqs ([r] r))]
               (try
                 (let [q-fn #(merge % {:path "/articles/search"
                                       :query {"q" q "limit" "100"}})
                       results (async/thread (get-edn q-fn))
                       results (merge {:query q} results)]
                   (bus/add-search-result results))
                 (catch Exception e (timbre/warn e)))
                 (recur)))
           stop-ch)
  :stop (async/close! search-results))
