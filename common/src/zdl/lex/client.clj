(ns zdl.lex.client
  (:require [clj-http.client :as http]
            [clojure.core.async :as a]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.url :refer [server-url]]
            [zdl.lex.util :refer [uuid]]
            [zdl.xml.util :as xml])
  (:import java.util.concurrent.Future))

(defn request->ch
  ([req]
   (request->ch (a/chan) req))
  ([ch req]
   (request->ch ch req nil))
  ([ch req cancel-ch]
   (let [req (merge req {:async? true})
         complete! (fn [result]
                     (when cancel-ch (a/close! cancel-ch))
                     (a/>!! ch result)
                     (a/close! ch))
         error! (comp complete! (fn [e] {::error e}))]
     (try
       (let [^Future f (http/request req complete! error!)]
         (when cancel-ch
           (a/go (when (a/<! cancel-ch) (. f (cancel true))))))
       (catch Throwable t (error! t)))
     ch)))

(def url
  (comp str server-url))

(def auth
  (delay
    (let [user (getenv "ZDL_LEX_SERVER_USER")
          password (getenv "ZDL_LEX_SERVER_PASSWORD")]
      (if (and user password) [user password]))))

(defn request
  [req]
  (-> (some->> auth deref (array-map :basic-auth))
      (merge {:request-method :get :accept :edn :as :clojure} req)))

(defn get-status
  []
  (request {:url (url "/status")}))

(defn create-article
  [form pos]
  (request {:request-method :put
            :url (url "/article/" {:form form :pos pos})}))

(def ^:dynamic *lock-token*
  (uuid))

(defn lock-resource
  ([id ttl]
   (request {:request-method :post
             :url (url "lock/" id {:ttl (str ttl) :token *lock-token*})})))

(defn unlock-resource
  ([id]
   (request {:request-method :delete
             :url (url "lock/" id {:token *lock-token*})})))

(defn id->article-url [id]
  (url "article/" id))

(defn get-article
  [id]
  (request {:url (id->article-url id) :accept "text/xml" :as :byte-array}))

(defn post-article
  [id xml]
  (request {:request-method :post :url (id->article-url id)
            :content-type "text/xml" :body xml
            :accept "text/xml" :as :byte-array}))

(defn search-articles
  ([q]
   (request {:url (url "/index" {:q q})}))
  ([q limit]
   (request {:url (url "/index" {:q q :limit (str limit)})})))

(defn export-article-metadata
  ([q]
   (request {:url (url "/index/export" {:q q})
             :accept "text/csv" :as :stream}))
  ([q limit]
   (request {:url (url "/index/export" {:q q :limit (str limit)})
             :accept "text/csv" :as :stream})))

(defn get-issues
  [q]
  (request {:url (url "/mantis/issues" {:q q})}))

(defn get-suggestions
  [q]
  (request {:url (url "/index/forms/suggestions" {:q q})}))
