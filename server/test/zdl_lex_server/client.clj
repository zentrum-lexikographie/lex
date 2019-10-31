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
            [zdl-lex-common.util :refer [file]]
            [zdl-lex-common.xml-schema :as schema])
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


(defn http-request
  "Async HTTP requests."
  [ch req]
  (->
   (merge req {:async? true})
   (http/request (partial a/>!! ch) #(do (timbre/warn %) (a/close! ch)))))

(def ^:private query-endpoint
  "Server endpoint for querying the Solr index."
  (.. (URL. (env :server-base)) (toURI) (resolve "/index") (toString)))

(defn run-query
  "Sends a given Solr index query as the given author."
  [author query]
  (let [ch (a/chan 1)]
    (http-request ch {:url query-endpoint :method :get
                      :basic-auth [author author]
                      :query-params {:q query :limit "1000"}
                      :as :json})
    (when-let [resp (a/<!! ch)]
      (timbre/info {:server (env :server-base)
                    :author author
                    :query query
                    :results (-> resp :body :total)}))))

(defn -main
  "Run a number of sample queries."
  [num-queries & args]
  (log/configure)
  (let [num-queries (Integer/parseInt num-queries)
        user-queries (gen/tuple author-generator query-generator)]
    (doseq [[author query] (gen/sample user-queries num-queries)]
      (run-query author query))))

(comment (-main))
