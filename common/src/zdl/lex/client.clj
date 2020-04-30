(ns zdl.lex.client
  (:require [clj-http.client :as http :refer [unexceptional-status?]]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.url :refer [server-url]]
            [zdl.lex.util :refer [uuid]]
            [zdl.xml.util :as xml]))

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
      (merge {:request-method :get :accept :edn :as :clojure} req)
      (http/request)
      :body))

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
  (xml/->dom
   (request {:url (id->article-url id) :accept "text/xml" :as :stream})))

(defn post-article
  [id xml]
  (xml/->dom
   (request {:request-method :post :url (id->article-url id)
             :content-type "text/xml" :body xml
             :accept "text/xml" :as :stream})))

(defn edit-article
  [id ttl ef]
  (try
    (lock-resource id ttl)
    (->> (get-article id) (ef id) (post-article id))
    (finally
      (unlock-resource id))))

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
