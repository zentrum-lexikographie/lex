(ns zdl-lex-client.http
  (:require [cemerick.url :refer [url]]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [taoensso.timbre :as timbre]
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

(defn- read-edn [con]
  (doto con
    (.setRequestProperty "Accept" "application/edn"))
  (-> (.getInputStream con)
      (slurp :encoding "UTF-8")
      (read-string)))

(defn- write-and-read-edn [msg]
  (fn [con]
    (doto con
      (.setDoOutput true)
      (.setRequestMethod "POST")
      (.setRequestProperty "Content-Type" "application/edn")
      (.setRequestProperty "Accept" "application/edn"))
    (-> (.getOutputStream con)
        (spit (pr-str msg) :encoding "UTF-8"))
    (-> (.getInputStream con)
        (slurp :encoding "UTF-8")
        (read-string))))

(def get-edn
  (partial tx read-edn))

(defn post-edn [uf msg]
  (tx (write-and-read-edn msg) uf))

(defn search-articles
  ([q] (search-articles q 1000))
  ([q limit]
   (timbre/debug {:q q :limit limit})
   (get-edn #(merge % {:path "/articles/search"
                       :query {"q" q "limit" (str limit)}}))))

(defn query-article-facets []
  (-> (search-articles "id:*" 0) :facets))

(defn suggest-forms [q]
  (get-edn #(merge % {:path "/articles/forms/suggestions"
                      :query {"q" q}})))

(defn sync-with-exist [id]
  (post-edn #(merge % {:path "/articles/exist/sync-id"
                       :query {"id" id}}) {}))

(defn get-status []
  (get-edn #(merge % {:path "/status"})))

