(ns zdl-lex-client.http
  (:require [aleph.http :as http]
            [aleph.http.client-middleware :refer [unexceptional-status?]]
            [byte-streams :as bs]
            [clojure.core.async :as a]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [manifold.deferred :as d]
            [mount.core :refer [defstate]]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.query :as query]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.util :refer [server-url]]
            [zdl-xml.util :as xml])
  (:import [java.io File IOException]
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
      (with-open [status (.. status-con (getInputStream))]
        (let [{:keys [user password]}
              (-> status (bs/to-string {:charset "UTF-8"}) read-string)]
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
    (= 423 status) (->
                    (if (map? body) body (-> body bs/to-string edn/read-string))
                    (lock->exception)
                    (d/error-deferred))
    :else (->> (ex-info  (str "status: " status) response)
               (IOException.)
               (d/error-deferred))))

(defn request
  [{:keys [url] :as req}]
  (-> (if-let [auth (request-auth)] {:basic-auth auth})
      (merge {:accept "application/edn"})
      (merge req {:url (str url)
                  :character-encoding "utf-8"
                  :throw-exceptions false})
      (http/request)
      (d/chain handle-errors)))

(defn get-xml [url]
  (->
   (request {:request-method :get :url url
             :accept "text/xml" :as :stream})
   (d/chain :body xml/->dom)))

(defn post-xml [url xml]
  (->
   (request {:request-method :post :url url
             :content-type "text/xml" :body xml
             :accept "text/xml" :as :stream})
   (d/chain :body xml/->dom)))

(defn get-edn [url]
  (->
   (request {:request-method :get :url url :accept :edn :as :clojure})
   (d/chain :body)))

(defn post-edn [url d]
  (->
   (request {:request-method :post :url url
             :content-type :edn :body (pr-str d)
             :accept :edn :as :clojure})
   (d/chain :body)))

(defn id->store-url [id]
  (server-url "article/" id))

(defn lock [id ttl token]
  (->
   (request {:request-method :post
             :url (server-url "lock/" id {:ttl (str ttl) :token token})
             :accept :edn :as :clojure})
   (d/chain :body)))

(defn unlock [id token]
  (->
   (request {:request-method :delete
             :url (server-url "lock/" id {:token token})
             :accept :edn :as :clojure})
   (d/chain :body)))

(defn get-issues [q]
  (get-edn (server-url "/mantis/issues" {:q q})))

(defn suggest-forms [q]
  (get-edn (server-url "/index/forms/suggestions" {:q q})))

(defn create-article [form pos]
  (->
   (request {:request-method :put
             :url (server-url "/article/" {:form form :pos pos})
             :accept :edn :as :clojure})
   (d/chain :body)))

(defn get-status []
  (d/chain
   (get-edn (server-url "/status"))
   (partial merge {:timestamp (java.time.Instant/now)})
   (partial bus/publish! :status)))

(defstate ping-status
  :start (cron/schedule "*/30 * * * * ?" "Get server status" get-status)
  :stop (a/close! ping-status))

(defn search-articles [req]
  (let [q (query/translate (req :query))]
    (d/chain
     (get-edn (server-url "/index" {:q q :limit "1000"}))
     (partial merge req)
     (partial bus/publish! :search-response))))

(defstate search-reqs->responses
  :start (bus/listen :search-request search-articles)
  :stop (search-reqs->responses))

(defn export [query ^File f]
  (let [q (query/translate query)]
    (-> 
     (request {:request-method :get
               :url (server-url "/index/export" {:q q :limit "50000"})
               :accept "text/csv" :as :stream})
     (d/chain :body #(io/copy % f)))))

(comment
  (request-auth)
  @(get-edn (server-url "/status"))
  @(get-issues "spitzfingrig")
  @(export "id:*" (io/file "test.csv"))
  (deref (search-articles {:query "spitz*"})))
