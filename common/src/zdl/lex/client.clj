(ns zdl.lex.client
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [clojure.data.csv :as csv]
            [lambdaisland.uri :as uri :refer [uri]]
            [manifold.deferred :as d]
            [zdl.lex.article.xml :as axml]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.url :refer [server-base]]
            [zdl.lex.util :refer [uuid]]
            [clojure.tools.logging :as log]))

(def ^:dynamic *auth*
  (let [user (getenv "SERVER_USER")
        password (getenv "SERVER_PASSWORD")]
    (when (and user password) [user password])))

(def ^:dynamic *insecure?*
  (getenv "HTTPS_INSECURE"))

(defn request
  ([url]
   (request url {}))
  ([url req]
   (request url :get req))
  ([url method req]
   (let [url (str (uri/join server-base url))]
     (http/request
      (cond-> req
        :always     (assoc :url url :request-method method)
        :always     (update-in [:headers "Accept"] #(or % "application/edn"))
        *auth*      (assoc :basic-auth *auth*)
        *insecure?* (assoc :insecure? true))))))

(defn decode-edn-response
  [response]
  (update response :body (comp read-string bs/to-string)))

(defn decode-xml-response
  [response]
  (update response :body axml/read-xml))

(defn decode-csv-response
  [response]
  (update response :body (comp vec csv/read-csv bs/to-reader)))

(defn get-status
  []
  (->
   (request "status")
   (d/chain decode-edn-response)))

(defn create-article
  [form pos]
  (->
   (uri "article/")
   (uri/assoc-query  :form form :pos pos)
   (request :put {})
   (d/chain decode-edn-response)))

(def ^:dynamic *lock-token*
  (uuid))

(defn lock-resource
  ([id ttl]
   (lock-resource id ttl *lock-token*))
  ([id ttl token]
   (->
    (uri/join "lock/" id)
    (uri/assoc-query :ttl (str ttl) :token token)
    (request :post {})
    (d/chain decode-edn-response))))

(defn unlock-resource
  ([id]
   (unlock-resource id *lock-token*))
  ([id token]
   (->
    (uri/join "lock/" id)
    (uri/assoc-query :token token)
    (request :delete {}))))

(defn get-article
  [id]
  (->
   (uri/join "article/" id)
   (request {:headers {"Accept" "text/xml"}})
   (d/chain decode-xml-response)))

(defn get-graph
  [id]
  (->
   (uri/join "graph/" id)
   (request)
   (d/chain decode-edn-response)))

(defn post-article
  ([id xml]
   (post-article id xml *lock-token*))
  ([id xml token]
   (->
    (uri/join "article/" id)
    (uri/assoc-query :token token)
    (request :post {:headers {"Content-Type" "text/xml" "Accept" "text/xml"}
                    :body    xml})
    (d/chain decode-xml-response))))

(defn search-articles
  [q & {:as params}]
  (->
   (uri "index")
   (uri/assoc-query* (assoc params :q q))
   (request)
   (d/chain decode-edn-response)))

(defn export-article-metadata
  [q & {:as params}]
  (->
   (uri "index/export")
   (uri/assoc-query* (assoc params :q q))
   (request {:headers {"Accept" "text/csv"}})
   #_(d/chain decode-csv-response)))

(defn get-issues
  [q]
  (->
   (uri "mantis/issues")
   (uri/assoc-query :q q)
   (request)
   (d/chain decode-edn-response)))

(defn get-forms-suggestions
  [q]
  (->
   (uri "index/forms/suggestions")
   (uri/assoc-query :q q)
   (request)
   (d/chain decode-edn-response)))

(comment
  @(d/catch (request "http://localhost:3000/statu") #(ex-data %))
  @(get-issues "spitzfingrig"))
