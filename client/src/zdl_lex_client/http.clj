(ns zdl-lex-client.http
  (:require [clj-http.client :as http :refer [unexceptional-status?]]
            [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.query :as query]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.util :refer [server-url]]
            [zdl-xml.util :as xml])
  (:import [java.io File IOException PushbackReader]
           ro.sync.exml.plugin.lock.LockException))

(def ^:private auth
  (atom 
   (let [user (env :server-user)
         password (env :server-password)]
     (if (and user password) [user password]))))

(defn request-auth
  []
  (if-let [auth @auth]
    auth
    (let [status-con (.. (server-url "/status") (openConnection))]
      (.. status-con (setRequestProperty "Accept" "application/edn"))
      (with-open [status-stream (.. status-con (getInputStream))
                  status-reader (io/reader status-stream :encoding "UTF-8")
                  status-reader (PushbackReader. status-reader)]
        (let [{:keys [user password]} (edn/read status-reader)]
          (if (and user password) (reset! auth [user password])))))))

(defn lock->owner
  [{:keys [owner owner_ip]}]
  (format "%s (IP: %s)" owner owner_ip))

(def ^:private readable-date-time-formatter
  (java.time.format.DateTimeFormatter/ofPattern "dd.MM.YYYY', 'HH:mm' Uhr'"))

(defn lock->message
  [{:keys [resource expires] :as lock}]
  (let [path (or (not-empty resource) "<alle>")
        owner (lock->owner lock)
        until (.. (java.time.Instant/ofEpochMilli expires)
                  (atZone (java.time.ZoneId/systemDefault))
                  (format readable-date-time-formatter))]
    (->> ["Artikel gesperrt"
          ""
          (format "Pfad: %s" path)
          (format "Von: %s" owner)
          (format "Ablaufdatum: %s" until)
          ""]
         (str/join \newline))))

(defn lock->exception [lock]
  (let [owner (lock->owner lock)
        message (lock->message lock)]
    (doto (LockException. message true message) (.setOwnerName owner))))

(defn handle-errors
  [{:keys [status body] :as response}]
  (cond
    (unexceptional-status? status) response
    (= 423 status) (-> body lock->exception throw)
    :else (->> (ex-info (str "status: " status) response) (IOException.) throw)))

(defn request
  [{:keys [url] :as req}]
  (-> (if-let [auth (request-auth)] {:basic-auth auth})
      (merge {:accept "application/edn"})
      (merge req {:url (str url) :throw-exceptions false :coerce :always})
      (http/request)
      (handle-errors)))

(defn get-xml [url]
  (->
   (request {:request-method :get :url url
             :accept "text/xml" :as :stream})
   :body xml/->dom))

(defn post-xml [url xml]
  (->
   (request {:request-method :post :url url
             :content-type "text/xml" :body xml
             :accept "text/xml" :as :stream})
   :body xml/->dom))

(defn get-edn [url]
  (->
   (request {:request-method :get :url url
             :accept :edn :as :clojure})
   :body))

(defn post-edn [url d]
  (->
   (request {:request-method :post :url url
             :content-type :edn :body (pr-str d)
             :accept "application/edn" :as :clojure})
   :body))

(defn id->store-url [id]
  (server-url "article/" id))

(defn lock [id ttl token]
  (->
   (request {:request-method :post
             :url (server-url "lock/" id {:ttl (str ttl) :token token})
             :accept :edn :as :clojure})
   :body))

(defn unlock [id token]
  (->
   (request {:request-method :delete
             :url (server-url "lock/" id {:token token})
             :accept :edn :as :clojure})
   :body))

(defn get-issues [q]
  (get-edn (server-url "/mantis/issues" {:q q})))

(defn suggest-forms [q]
  (get-edn (server-url "/index/forms/suggestions" {:q q})))

(defn create-article [form pos]
  (->
   (request {:request-method :put
             :url (server-url "/article/" {:form form :pos pos})
             :accept :edn :as :clojure})
   :body))

(defn get-status []
  (->>
   (get-edn (server-url "/status"))
   (merge {:timestamp (java.time.Instant/now)})
   (bus/publish! :status)))

(defstate ping-status
  :start (cron/schedule "*/30 * * * * ?" "Get server status" get-status)
  :stop (a/close! ping-status))

(defn search-articles [req]
  (let [q (query/translate (req :query))]
    (->>
     (get-edn (server-url "/index" {:q q :limit "1000"}))
     (merge req)
     (bus/publish! :search-response))))

(defstate search-reqs->responses
  :start (bus/listen :search-request search-articles)
  :stop (search-reqs->responses))

(defn export [query ^File f]
  (let [q (query/translate query)]
    (->
     (request {:request-method :get
               :url (server-url "/index/export" {:q q :limit "50000"})
               :accept "text/csv" :as :stream})
     :body (io/copy f))))

(comment
  (request-auth)
  (get-edn (server-url "/status"))
  (get-issues "spitzfingrig")
  (export "id:*" (io/file "test.csv"))
  (search-articles {:query "spitz*"}))
