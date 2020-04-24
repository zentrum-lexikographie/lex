(ns zdl.lex.server.solr.client
  (:require [clojure.tools.logging :as log]
            [metrics.timers :refer [deftimer time!]]
            [zdl.lex.article :as article]
            [zdl.lex.article.fs :as afs]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.util :refer [relativize]]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.http-client :as http-client]
            [zdl.lex.server.solr.doc :refer [article->fields]]
            [zdl.lex.server.solr.query :as query]
            [zdl.xml.util :as xml]))

(def req
  "Solr client request fn with configurable base URL and authentication"
  (delay
    (let [client
          (http-client/configure
           (getenv ::solr-user "ZDL_LEX_SOLR_USER")
           (getenv ::solr-password "ZDL_LEX_SOLR_PASSWORD"))
          base-url
          (str
           (getenv ::solr-url "ZDL_LEX_SOLR_URL" "http://localhost:8983/solr/")
           (getenv ::solr-core "ZDL_LEX_SOLR_CORE" "articles"))]
      (fn [{:keys [url] :as req}]
        (client (assoc req :url (str base-url url)))))))

(deftimer [solr client query-timer])

(defn query [params]
  (->>
   (@req {:method :get :url "/query"
          :query-params params
          :as :json})
   (time! query-timer)))

(deftimer [solr client suggest-timer])

(defn suggest [name q]
  (->>
   (@req {:method :get :url "/suggest"
          :query-params {"suggest.dictionary" name "suggest.q" q}
          :throw-exceptions false :coerce :always
          :as :json})
   (time! suggest-timer)))

(defn build-suggestions [name]
  (@req {:method :get :url "/suggest"
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
  (doall (pmap (comp @req (partial merge update-req)) updates)))

(def commit-optimize
  (partial batch-update [{:body "<update><commit/><optimize/></update>"
                          :content-type :xml}]))

(defn update-articles [articles->xml articles]
  (batch-update (->> articles
                     (partition-all update-batch-size)
                     (pmap articles->xml)
                     (map #(array-map :body (xml/serialize %)
                                      :content-type :xml)))))

(defn- articles->add-xml [article-files]
  (let [f->id (comp str (partial relativize @git/dir))
        doc (xml/new-document)
        el #(.createElement doc %)
        add (doto (el "add") (.setAttribute "commitWithin" "10000"))]
    (try
      (doseq [f article-files :let [id (f->id f)]
              a (article/file->articles f id) :let [article-doc (el "doc")]]
        (doseq [[n v] (article->fields a)]
          (doto article-doc
            (.appendChild
             (doto (el "field")
               (.setAttribute "name" n)
               (.setTextContent v)))))
        (doto add (.appendChild article-doc)))
      (catch Exception e (log/warn e)))
    (doto doc (.appendChild add))))

(def add-articles (partial update-articles articles->add-xml))

(defn- articles->delete-xml [article-files]
  (let [doc (xml/new-document)
        el #(.createElement doc %)
        del (doto (el "delete") (.setAttribute "commitWithin" "10000"))]
    (doseq [id (map git/file->id article-files)]
      (doto del
        (.appendChild
         (doto (el "id") (.setTextContent id)))))
    (doto doc (.appendChild del))))

(def delete-articles (partial update-articles articles->delete-xml))

(defn- query->delete-xml [[query]]
  (let [doc (xml/new-document)
        el #(.createElement doc %)]
    (doto doc
      (.appendChild
       (doto (doto (el "delete") (.setAttribute "commitWithin" "10000"))
         (.appendChild
          (doto (el "query") (.setTextContent query))))))))

(deftimer [solr client index-rebuild-timer])

(defn rebuild-index []
  (->>
   (let [sync-start (System/currentTimeMillis)
         articles (afs/files @git/dir)]
     (when-not (empty? (doall (add-articles articles)))
       (update-articles query->delete-xml
                        [(format "time_l:[* TO %s}" sync-start)])
       (commit-optimize))
     articles)
   (time! index-rebuild-timer)))

(defn index-empty? []
  (= 0 (get-in (query {"q" "id:*" "rows" "0"}) [:body :response :numFound] -1)))

(defn id-exists? [id]
  (let [q [:query
           [:clause
            [:field [:term "id"]]
            [:value [:pattern (str "*" id "*")]]]]]
    (some->> (query {"q" (query/ast->str q) "rows" 0})
             :body :response :numFound
             (< 0))))
