(ns zdl-lex-server.client
  (:require [clj-http.client :as http]
            [clojure.core.async :as a]
            [clojure.data.zip :as dz]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.zip :as zip]
            [taoensso.timbre :as timbre]
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

(defn query-article
  [author q]
  (a/go
    (let [req (assoc-in query-template [:query-params :q] q)]
      (when-let [resp (a/<! (http-request author (a/chan) req))]
        (some->> resp :body :result not-empty rand-nth :id)))))

(defn- download-req [id]
  {:method :get
   :url (.. (URL. (env :server-base)) (toURI)
            (resolve "/article/")
            (resolve (path->uri id))
            (toString))})

(defn download-article
  [author id]
  (a/go
    (when-let [resp (a/<! (http-request author (a/chan) (download-req id)))]
      (some->> resp :body xml/->dom
               article/doc->articles
               (map article/excerpt)
               (mapcat :forms)
               (vec)))))

(defn run-query
  "Sends a given Solr index query as the given author."
  [author query]
  (a/go
    (some->> (query-article author query)
             (a/<!)
             (download-article author)
             (a/<!))))

(defn -main
  "Run a number of sample queries."
  [num-queries & args]
  (log/configure)
  (let [num-queries (Integer/parseInt num-queries)
        user-queries (gen/tuple author-generator query-generator)]
    (doseq [[author query] (gen/sample user-queries num-queries)]
      (timbre/info (a/<!! (run-query author query))))))

(comment (-main "100"))
