(ns zdl-lex-client.http
  (:require [cemerick.url :refer [url]]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [manifold.time :as mt]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.env :refer [config]]
            [zdl-lex-client.query :as query]
            [zdl-lex-common.xml :as xml])
  (:import java.io.IOException
           [java.net ConnectException URL]))

(def ^:private webdav-base (config :webdav-base))

(defn webdav? [^URL u]
  (str/starts-with? (str u) webdav-base))

(defn id->url [id]
  (URL. (str (url webdav-base id))))

(defn url->id [^URL u]
  (if (webdav? u) (subs (str u) (inc (count webdav-base)))))

(def server-base (config :server-base))

(def ^:private basic-creds
  (let [{:keys [user password]} (config :server-auth)]
    (if (and user password)
      (apply str (map char (base64/encode (.getBytes (str user ":" password))))))))

(defn server-url
  ([path]
   (server-url path {}))
  ([path params]
   (URL. (str (merge (url server-base) {:path path :query params})))))

(defn tx [callback url]
  (let [con (.. url (openConnection))]
    (timbre/debug (str url))
    (try
      (when basic-creds
        (.setRequestProperty con "Authorization" (str "Basic " basic-creds)))
      (callback con)
      (catch ConnectException e
        (throw (ex-info "I/O error while connecting to XML database" {} e)))
      (catch IOException e
        (with-open [err (io/reader (.getErrorStream con) :encoding "UTF-8")]
          (throw (ex-info "I/O error while talking to server"
                          {:http-status (.getResponseCode con)
                           :http-message (.getResponseMessage con)
                           :http-body (slurp err)})))))))

(defn- read-xml [con]
  (doto con
    (.setRequestProperty "Accept" "application/xml"))
  (-> (.getInputStream con)
      (xml/parse)))

(def get-xml (partial tx read-xml))

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

(defn post-edn [url msg]
  (tx (write-and-read-edn msg) url))

(defn get-issues [q]
  (get-edn (server-url "/articles/issues" {"q" q})))

(defn suggest-forms [q]
  (get-edn (server-url "/articles/forms/suggestions" {"q" q})))

(defn sync-with-exist [id]
  (post-edn (server-url "/articles/exist/sync-id" {"id" id}) {}))

(defstate get-status
  :start (mt/every
          (mt/seconds 30)
          (fn []
            (d/chain (d/future (get-edn (server-url "/status")))
                     (partial merge {:timestamp (t/now)})
                     (partial bus/publish! :status))))
  :stop (get-status))

(defstate search-articles
  :start (let [subscription (bus/subscribe :search-request)]
           (-> (fn [req]
                 (d/chain (query/translate (req :query))
                          #(server-url "/articles/search" {"q" % "limit" "1000"})
                          #(future (get-edn %))
                          (partial merge req)
                          (partial bus/publish! :search-response)))
               (s/consume subscription))
           subscription)
  :stop (s/close! search-articles))
