(ns zdl-lex-server.solr.client
  (:require [lucene-query.core :as lucene]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.http-client :as http-client]
            [zdl-lex-server.solr.doc :refer [article->fields]]))

(def req
  (http-client/configure (env :solr-user) (env :solr-password)))

(def url
  (partial str (env :solr-base) (env :solr-core)))

(defn suggest [name q]
  (req {:method :get :url (url "/suggest")
        :query-params {"suggest.dictionary" name "suggest.q" q}
        :as :json}))

(defn query [params]
  (req {:method :get :url (url "/query")
        :query-params params
        :as :json}))

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

(def ^:private update-batch-size 2000)

(def ^:private update-req
  {:method :post
   :url (url "/update")
   :query-params {:wt "json"}
   :as :json})

(defn batch-update [updates]
  (doall (pmap (comp req (partial merge update-req)) updates)))

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
  (let [articles (article/articles git/articles-dir)
        doc (xml/new-document)
        el #(.createElement doc %)
        add (doto (el "add") (.setAttribute "commitWithin" "10000"))]
    (doseq [file article-files]
      (try
        (doseq [{:keys [id] :as article} (articles file)
                :let [article-doc (el "doc")]]
          (doseq [[n v] (article->fields article)]
            (doto article-doc
              (.appendChild
               (doto (el "field")
                 (.setAttribute "name" n)
                 (.setTextContent v)))))
            (doto add (.appendChild article-doc)))
        (catch Exception e (timbre/warn e file))))
    (doto doc (.appendChild add))))

(def add-articles (partial update-articles articles->add-xml))

(defn- articles->delete-xml [article-files]
  (let [file->id (article/file->id git/articles-dir)
        doc (xml/new-document)
        el #(.createElement doc %)
        del (doto (el "delete") (.setAttribute "commitWithin" "10000"))]
    (doseq [id (map file->id article-files)]
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

(defn rebuild-index []
  (let [sync-start (System/currentTimeMillis)
        articles (article/article-xml-files git/articles-dir)]
    (when-not (empty? (doall (add-articles articles)))
      (update-articles query->delete-xml [(format "time_l:[* TO %s}" sync-start)])
      (commit-optimize))
    articles))

(defn build-suggestions [name]
  (req {:method :get :url (url "/suggest")
        :query-params {"suggest.dictionary" name "suggest.buildAll" "true"}
        :as :json}))

(def ^:private build-forms-suggestions
  (partial build-suggestions "forms"))

(defn id-exists? [id]
  (let [q [:query
           [:clause
            [:field [:term "id"]]
            [:value [:pattern (str "*" id "*")]]]]]
    (some->> (query {"q" (query/ast->str q) "rows" 0})
             :body :response :numFound
             (< 0))))
