(ns zdl-lex-server.client
  (:require [clj-http.client :as http]
            [clojure.core.async :as a]
            [clojure.data.zip :as dz]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.zip :as zip]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.log :as log]
            [zdl-lex-common.url :refer [path->uri]]
            [zdl-lex-common.util :refer [file]]
            [zdl-lex-common.xml-schema :as schema]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-common.article :as article])
  (:import java.net.URL))

(def ^:private article-attr?
  "Predicate for `<Article/>` attributes."
  (schema/attr-of? "{http://www.dwds.de/ns/1.0}Artikel"))

(defn- attr-values
  "Collect `<Article/>` attributes of a schema."
  [schema attr-name]
  (schema/collect-values
   schema dz/descendants :attribute
   article-attr? (schema/name= attr-name)))

(def ^:private schema-values
  "Essential values from RELAX NG schema, e.g. authors, article types etc."
  (delay
    (let [schema (->> (file "../oxygen/framework/rng/DWDSWB.rng")
                      (schema/parse) (schema/resolve-refs)
                      (zip/xml-zip))
          attr-values (partial attr-values schema)]
      {:sources (attr-values "Quelle")
       :authors (attr-values "Autor")
       :types (attr-values "Typ")
       :status (attr-values "Status")})))

(def author-generator
  "Generate authors."
  (let [{:keys [authors]} @schema-values]
    (s/gen (disj authors "DWDS"))))

(def query-generator
  "Generate queries (prefix patterns combined with source filter)."
  (let [{:keys [sources]} @schema-values]
    (gen/fmap
     (fn [[c source]] (format "forms:%s* AND source:\"%s\"" c source))
     (gen/tuple (gen/char-alpha) (s/gen sources)))))


(defn- http-on-response [ch resp]
  (a/>!! ch resp))

(defn- http-on-error [ch e]
  (timbre/warn e)
  (a/close! ch))

(defn http-request
  "Async HTTP requests."
  [author ch req]
  (http/request
   (merge req {:basic-auth [author author] :async? true})
   (partial http-on-response ch)
   (partial http-on-error ch))
  ch)

(def ^:private query-template
  {:url (.. (URL. (env :server-base)) (toURI) (resolve "/index") (toString))
   :method :get
   :query-params {:limit "1000"} :as :json})

(defn query-random-article
  [{:keys [author query] :as tx}]
  (let [req (assoc-in query-template [:query-params :q] query)]
    (a/go
      (->> (some->> (a/<! (http-request author (a/chan) req))
                    :body :result not-empty rand-nth :id)
           (assoc tx :id)))))

(defn transfer-article
  [{:keys [author id xml] :as tx}]
  (a/go
    (if-not id
      tx
      (let [req {:method (if xml :post :get)
                 :body xml
                 :url (.. (URL. (env :server-base)) (toURI)
                          (resolve "/article/")
                          (resolve (path->uri id))
                          (toString))}]
        (->> (some->> (a/<! (http-request author (a/chan) req)) :body)
             (assoc tx :xml))))))

(defn edit-article
  [{:keys [id author xml] :as tx}]
  (if-let [doc (some-> xml xml/->dom)]
    (let [element-by-name #(-> (.getElementsByTagName doc %) xml/->seq first)
          timestamp (t/format :iso-local-date (t/date))]
      (doto (element-by-name "Artikel")
        (.setAttribute "Zeitstempel" timestamp)
        (.setAttribute "Autor" author))
      (assoc tx :xml (xml/serialize doc)))
    tx))

(defn run-transaction
  "Sends a given Solr index query as the given author."
  [tx]
  (a/go
    (->> tx
         (query-random-article) (a/<!)
         (transfer-article) (a/<!)
         (edit-article)
         (transfer-article) (a/<!))))

(defn -main
  "Run a number of sample queries."
  [num-queries & args]
  (log/configure)
  (let [num-queries (Integer/parseInt num-queries)
        user-queries (gen/tuple author-generator query-generator)]
    (doseq [[author query] (gen/sample user-queries num-queries)]
      (timbre/info (:id (a/<!! (run-transaction {:author author :query query})))))))

(comment (-main "10"))
