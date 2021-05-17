(ns zdl.lex.server.solr.client
  (:require [clojure.data.xml :as dx]
            [clojure.tools.logging :as log]
            [metrics.timers :refer [deftimer time!]]
            [zdl.lex.article :as article]
            [zdl.lex.article.fs :as afs]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :refer [relativize]]
            [zdl.lex.lucene :as lucene]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.http-client :as http-client]
            [zdl.lex.server.solr.doc :refer [article->fields]]))

(def ^:private client
  (http-client/configure (getenv "SOLR_USER") (getenv "SOLR_PASSWORD")))

(def ^:private base-url
  (str
   (getenv "SOLR_URL" "http://localhost:8983/solr/")
   (getenv "SOLR_CORE" "articles")))

(defn req
  "Solr client request fn with configurable base URL and authentication"
  [{:keys [url] :as req}]
  (client (assoc req :url (str base-url url))))

(deftimer [solr client query-timer])

(defn query [params]
  (->>
   (req {:method :get :url "/query"
         :query-params params
         :as :json})
   (time! query-timer)))

(deftimer [solr client suggest-timer])

(defn suggest [name q]
  (->>
   (req {:method :get :url "/suggest"
         :query-params {"suggest.dictionary" name "suggest.q" q}
         :throw-exceptions false :coerce :always
         :as :json})
   (time! suggest-timer)))

(defn build-suggestions [name]
  (req {:method :get :url "/suggest"
        :query-params {"suggest.dictionary" name "suggest.buildAll" "true"}
        :as :json}))

(deftimer [solr client forms-suggestions-build-timer])

(defn build-forms-suggestions []
  (->>
   (build-suggestions "forms")
   (time! forms-suggestions-build-timer)))

(defn scroll
  ([params] (scroll params 20000))
  ([params page-size] (scroll params page-size 0))
  ([params page-size page]
   (let [offset (* page page-size)
         resp (query (merge params {"start" offset "rows" page-size}))
         docs (get-in resp [:body :response :docs] [])]
     (concat
      docs
      (if-not (< (count docs) page-size)
        (lazy-seq (scroll params page-size (inc page))))))))

(def ^:private update-batch-size 10000)

(def ^:private update-req
  {:method :post
   :url "/update"
   :query-params {:wt "json"}
   :as :json})

(defn batch-update [updates]
  (doall (pmap (comp req (partial merge update-req)) updates)))

(def commit-optimize
  (partial batch-update [{:body "<update><commit/><optimize/></update>"
                          :content-type :xml}]))

(defn update-articles [articles->xml articles]
  (batch-update
   (->>
    articles
    (partition-all update-batch-size)
    (pmap articles->xml)
    (map (fn [node]
           {:body         (dx/emit-str (dx/sexp-as-element node))
            :content-type :xml})))))

(defn- articles->add-xml [article-files]
  [:add {:commitWithin "10000"}
   (try
     (for [f article-files :let [id (str (relativize git/dir f))]
           a (article/extract-articles {:file f :id id})]
       [:doc
        (for [[n v] (article->fields a)]
          [:field {:name n} v])])
     (catch Exception e (log/warn e)))])

(def add-articles (partial update-articles articles->add-xml))

(defn- articles->delete-xml [article-files]
  [:delete {:commitWithin "10000"}
   (for [id (map git/file->id article-files)]
     [:id id])])

(def delete-articles (partial update-articles articles->delete-xml))

(defn- query->delete-xml [[query]]
  [:delete {:commitWithin "10000"}
   [:query query]])

(deftimer [solr client index-rebuild-timer])

(defn clear-index []
  (update-articles query->delete-xml ["id:*"])
  (commit-optimize))

(defn rebuild-index []
  (->>
   (let [sync-start (System/currentTimeMillis)
         articles (afs/files git/dir)]
     (doall (add-articles articles))
     (update-articles query->delete-xml
                      [(format "time_l:[* TO %s}" sync-start)])
     (commit-optimize)
     articles)
   (time! index-rebuild-timer)))

(defn index-empty? []
  (= 0 (get-in (query {"q" "id:*" "rows" "0"}) [:body :response :numFound] -1)))

(defn id-exists? [id]
  (let [q [:query
           [:clause
            [:field [:term "id"]]
            [:value [:pattern (str "*" id "*")]]]]]
    (some->> (query {"q" (lucene/ast->str q) "rows" 0})
             :body :response :numFound
             (< 0))))
