(ns zdl.lex.server.solr.client
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [cheshire.core :as json]
            [clojure.data.xml :as dx]
            [clojure.tools.logging :as log]
            [lambdaisland.uri :as uri :refer [uri]]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [metrics.timers :as timers :refer [deftimer]]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.lucene :as lucene]
            [zdl.lex.server.solr.doc :refer [article->doc]])
  (:import java.util.concurrent.TimeUnit))

(def auth
  (let [user     (getenv "SOLR_USER")
        password (getenv "SOLR_PASSWORD")]
    (when (and user password) [user password])))

(def base-url
  (uri/join
   (getenv "SOLR_URL" "http://localhost:8983/solr/")
   (str (getenv "SOLR_CORE" "articles") "/")))

(defn request
  "Solr client request fn with configurable base URL and authentication"
  [{:keys [url] :as req}]
  (http/request
   (cond-> req
     :always (assoc :url (str (uri/join base-url url)))
     :always (update :request-method #(or % :get))
     auth    (assoc :basic-auth auth))))

(defn decode-json-response
  [response]
  (update response :body (comp #(json/parse-stream % true) bs/to-reader)))

(defn update-timer!
  [timer {:keys [request-time] :as response}]
  (when request-time
    (timers/update! timer request-time TimeUnit/MILLISECONDS))
  response)

(deftimer [solr client query-timer])

(defn query
  [params]
  (->
   (request {:url (uri/assoc-query* (uri "query") params)})
   (d/chain decode-json-response #(update-timer! query-timer %))))

(comment
  @(query {:q "id:*"}))

(defn scroll
  ([params]
   (scroll params 20000))
  ([params page-size]
   (let [pages (s/stream)]
     (d/loop [page 0]
       (when-not (s/closed? pages)
         (->
          (query (assoc params
                        :start (* page page-size)
                        :rows page-size))
          (d/chain (fn [response]
                     (get-in response [:body :response :docs] []))
                   (fn [docs]
                     (when (seq docs)
                       (s/put! pages docs))
                     (if (< (count docs) page-size)
                       (s/close! pages)
                       (d/recur (inc page)))))
          (d/catch (fn [e]
                     (log/debugf e "Error while paging through %s" params)
                     (s/close! pages))))))
     (s/source-only pages))))

(defn index-empty?
  []
  (d/chain
   (query {:q "id:*" :rows 0})
   (fn [response] (= 0 (get-in response [:body :response :numFound] -1)))))

(defn id->query
  [id]
  (lucene/ast->str [:query
                    [:clause
                     [:field [:term "id"]]
                     [:value [:pattern (str "*" id "*")]]]]))

(defn id-exists?
  [id]
  (d/chain
   (query {:q (id->query id) :rows 0})
   (fn [response] (< 0 (get-in response [:body :response :numFound] -1)))))

(deftimer [solr client suggest-timer])

(defn suggest
  [name q]
  (->
   (request {:url (->
                   (uri "suggest")
                   (uri/assoc-query "suggest.dictionary" name
                                    "suggest.q" q))})
   (d/chain decode-json-response #(update-timer! suggest-timer %))))

(deftimer [solr client forms-suggestions-build-timer])

(defn build-forms-suggestions
  []
  (->
   (request {:url (->
                   (uri "suggest")
                   (uri/assoc-query "suggest.dictionary" "forms"
                                    "suggest.buildAll" "true"))})
   (d/chain decode-json-response
            #(update-timer! forms-suggestions-build-timer %))))

(deftimer [solr client update-timer])

(defn request-update
  [xml-node]
  (try
    (->
     (request
      {:request-method :post
       :url            (uri/assoc-query (uri "update") :wt "json")
       :headers        {"Content-Type" "text/xml"}
       :body           (dx/emit-str xml-node)})
     (d/chain decode-json-response #(update-timer! update-timer %)))
    (catch Throwable t
      (log/info xml-node)
      (throw t))))

(defn request-updates
  [updates]
  (let [s' (s/stream)]
    (s/connect-via
     updates
     (fn [update]
       (->
        (request-update update)
        (d/chain #(s/put! s' %))
        (d/catch (fn [e]
                   (s/put! s'
                           (assoc (or (ex-data e) {::error e}) ::error? true))
                   (s/close! s')))))
     s')
    (s/source-only s')))

(defn consume-update
  [description {::keys [error?] :as update}]
  (let [level (if error? :warn :debug)]
    (when (log/enabled? level)
      (let [ks (cond-> [:status :request-time]
                 error? (conj ::error :headers :body))]
        (log/log level [description (select-keys update ks)]))))
  (d/success-deferred true))

(def commit-optimize-xml
  (dx/sexp-as-element [:update [:commit] [:optimize]]))

(defn articles->add-xmls
  [articles]
  (sequence
   (comp
    (map article->doc)
    (partition-all 10000)
    (map #(dx/sexp-as-element [:add {:commitWithin "10000"} (seq %)])))
   articles))

(defn articles->delete-xmls
  [articles]
  (sequence
   (comp
    (map :id)
    (partition-all 10000)
    (map #(dx/sexp-as-element [:delete {:commitWithin "10000"}
                               (for [id %] [:id id])])))
   articles))

(defn query->delete-xml
  [query]
  (dx/sexp-as-element [:delete {:commitWithin "10000"} [:query query]]))

(defn add-articles
  [& args]
  (throw (ex-info "Unsupported operation" {})))

(defn delete-articles
  [& args]
  (throw (ex-info "Unsupported operation" {})))

(defn add-to-index
  [articles]
  (s/consume-async
   #(consume-update "Index addition" %)
   (request-updates (articles->add-xmls articles))))

(defn remove-from-index
  [articles]
  (s/consume-async
   #(consume-update "Index removal" %)
   (request-updates (articles->delete-xmls articles))))

(deftimer [solr client index-rebuild-timer])

(defn rebuild-index
  [articles]
  (let [start (System/currentTimeMillis)]
    (->
     (s/consume-async
      #(consume-update "Index rebuild" %)
      (request-updates
       (concat
        (articles->add-xmls articles)
        [(query->delete-xml (format "time_l:[* TO %s}" start))
         commit-optimize-xml])))
     (d/catch #(log/warnf % "Error while rebuilding index"))
     (d/finally
       #(timers/update!
         index-rebuild-timer
         (- (System/currentTimeMillis) start) TimeUnit/MILLISECONDS)))))

(defn clear-index
  []
  (s/consume-async
   #(consume-update "Index purge" %)
   (request-updates [(query->delete-xml "id:*") commit-optimize-xml])))
