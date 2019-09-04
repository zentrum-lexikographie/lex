(ns zdl-lex-client.http
  (:require [cemerick.url :refer [url]]
            [clojure.core.async :as a]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.query :as query]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.xml :as xml])
  (:import [java.io File IOException]
           [java.net ConnectException URI URL]))

(def ^:private webdav-uri
  (-> (env :zdl-lex-webdav-base "https://lex.dwds.de/exist/webdav/db/dwdswb/data")
      (str "/")
      (URI.)))

(defn webdav? [^URL u]
  (str/starts-with? (str (.toURI u)) (str webdav-uri)))

(defn path->uri [path] (URI. nil nil path nil))

(defn id->url [id]
  (.. webdav-uri (resolve (path->uri id)) (toURL)))

(defn url->id [^URL u]
  (.. webdav-uri (relativize (.toURI u)) (getPath)))

(comment
  (-> "WDG/ve/Verfasserkollektiv-E_k_6565.xml" id->url))

(def server-base (env :zdl-lex-server-base "https://lex.dwds.de/"))

(def ^:private basic-creds
  (let [user (env :zdl-lex-server-auth-user)
        password (env :zdl-lex-server-auth-password)]
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
  (doto con (.setRequestProperty "Accept" "application/xml"))
  (xml/->dom (.getInputStream con)))

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

(defn create-article [form pos]
  (post-edn (server-url "/articles/create" {"form" form "pos" pos}) {}))

(defn get-status []
  (->> (get-edn (server-url "/status"))
       (merge {:timestamp (t/now)})
       (bus/publish! :status)))

(defstate ping-status
  :start (cron/schedule "*/30 * * * * ?" "Get server status" get-status)
  :stop (a/close! ping-status))

(defn search-articles [req]
  (let [q (query/translate (req :query))]
    (->> (get-edn (server-url "/articles/search" {"q" q "limit" "1000"}))
         (merge req)
         (bus/publish! :search-response))))

(defstate search-reqs->responses
  :start (bus/listen :search-request search-articles)
  :stop (search-reqs->responses))

(defn- send-change-notification [[url _]]
  (try
    (let [id (url->id url)]
      (post-edn (server-url "/articles/exist/sync-id" {"id" id}) {}))
    (catch Exception e (timbre/warn e))))

(defstate send-change-notifications
  :start (bus/listen :editor-saved send-change-notification)
  :stop (send-change-notifications))

(defn save-csv-to-file [^File f]
  (fn [con]
    (doto con
      (.setRequestProperty "Accept" "text/csv"))
    (io/copy (.getInputStream con) (io/output-stream f))))

(defn export [query ^File f]
  (let [q (query/translate query)]
    (tx (save-csv-to-file f)
        (server-url "/articles/export" {"q" q "limit" "50000"}))))
