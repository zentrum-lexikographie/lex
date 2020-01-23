(ns zdl-lex-client.http
  (:require [clojure.core.async :as a]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [aleph.http :as http]
            [byte-streams :as bs]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.query :as query]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.util :refer [server-url]]
            [zdl-lex-common.xml :as xml])
  (:import [java.io File IOException]
           [java.net ConnectException Authenticator Authenticator$RequestorType PasswordAuthentication URL]))

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

(defn request
  [{:keys [url] :as req}]
  (->> (if-let [auth (request-auth)] {:basic-auth auth})
       (merge req {:url (str url) :character-encoding "utf-8"})
       (http/request)))

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
             :accept :edn :as :clojure
             :throw-exceptions false})
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
             :url (server-url "/article" {:form form :pos pos})
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
