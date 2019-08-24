(ns zdl-lex-server.exist
  (:require [clj-http.client :as http]
            [clojure.core.async :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [mount.core :as mount :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-common.xml :as xml]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store])
  (:import java.net.URI))

(def ^:private req
  (comp #(timbre/spy :trace %)
        #(dissoc % :http-client)
        http/request
        (partial merge (config :exist-req))
        #(timbre/spy :trace %)))

(defn path->uri [path] (URI. nil nil path nil))

(def ^:private articles-path "/db/dwdswb/data")

(def ^:private webdav-uri
  (.. (URI. (str (config :exist-base) "/"))
      (resolve (path->uri (str "webdav" articles-path "/")))))

(defn- id->uri [id]
  (.. webdav-uri (resolve (path->uri id)) (toString)))

(defn- uri->id [uri]
  (.. webdav-uri (relativize (URI. uri)) (getPath)))

(comment
  (-> "DWDS/MWA-001/graue Maus.xml" id->uri uri->id))

(defn copy [id]
  (let [store-file (store/id->file id)
        exist-xml-req {:method :get
                       :url (str (id->uri id))
                       :as :stream}]
    (with-open [exist-xml (-> exist-xml-req req :body)]
      (locking store/git-dir
        (-> store-file fs/parent fs/mkdirs)
        (io/copy exist-xml store-file)
        store-file))
    id))

(defn delete [id]
  (locking store/git-dir
    (-> id store/id->file fs/delete)))

(def ex-ns "http://exist.sourceforge.net/NS/exist")

(def ^:private rest-uri-str
  (.. (URI. (str (config :exist-base) "/"))
      (resolve (path->uri (str "rest" articles-path "/")))
      (toString)))

(defn- xquery->xml [q]
  (let [query-doc (xml/new-document)
        element #(.createElementNS query-doc ex-ns %)]
    (-> (.appendChild query-doc
                      (doto (element "ex:query")
                        (.setAttribute "max" "1000000")
                        (.setAttribute "cache" "no")))
        (.appendChild (element "ex:text"))
        (.appendChild (.createCDATASection query-doc q))
        (.getOwnerDocument)
        (xml/serialize))))

(defn xquery [q]
  (-> {:method :post
       :url rest-uri-str
       :content-type :xml
       :body (xquery->xml q)}
      req :body))

(def ^:private articles-xquery
  (-> "toc.xq" io/resource (slurp :encoding "UTF-8") (format articles-path)))

(def ^:private article-docs
  (comp seq (xml/xpath-fn "//doc")))

(def ^:private article-doc-uri
  (comp uri->id
        #(.. webdav-uri (resolve (URI. %)) (toString))
        #(str/replace % articles-path ".")
        str
        (xml/xpath-fn "uri/text()")))

(def ^:private article-doc-modified
  (comp t/instant t/parse str (xml/xpath-fn "modified/text()")))

(defn article? [id]
  (not (#{"indexedvalues.xml"} id)))

(defn docs->articles [docs]
  (for [doc docs
        :let [id (article-doc-uri doc)
              modified (article-doc-modified doc)]
        :when (article? id)]
    {:id id :modified modified}))

(defn articles []
  (let [xquery-result (xquery articles-xquery)
        articles (-> xquery-result (xml/parse)
                     (article-docs) (docs->articles))]
    (when (empty? articles)
      (throw (ex-info "Empty article set" {:xquery-result xquery-result})))
    articles))

(defn articles->changeset [articles change-period]
  (let [existing (->> articles (map :id) (into #{}))
        removed (->> (store/article-files)
                     (map store/file->id)
                     (remove existing))
        added (->> (map store/id->file existing)
                   (remove fs/exists?)
                   (map store/file->id))
        changed? (partial t/< (t/- (t/now) change-period))
        changed (->> articles
                     (filter (comp changed? :modified))
                     (map :id)
                     (remove (into #{} added)))]
    {:added added :changed changed :removed removed}))

(defn- sync-changes [change-threshold]
  (let [changeset (articles->changeset (articles) change-threshold)
        {:keys [added changed removed]} changeset]
    (dorun (pmap copy (concat added changed)))
    (dorun (pmap delete removed))
    changeset))

(def ^:private short-term-sync (partial sync-changes (t/new-duration 1 :hours)))

(def ^:private long-term-sync (partial sync-changes (t/new-duration 2 :days)))

(defstate short-exist->git
  :start (cron/schedule "0 */15 0,2-23 * * ?" "eXist-db Sync. (last hour)"
                        short-term-sync)
  :stop (a/close! short-exist->git))

(defstate long-exist->git
  :start (cron/schedule "0 0 1 * * ?" "eXist-db Sync. (last days)"
                        long-term-sync)
  :stop (a/close! long-exist->git))

(defn handle-article-sync [{{:keys [id]} :params}]
  (htstatus/ok {id (copy id)}))

(defn handle-period-sync [{{:keys [amount unit] :as params} :path-params}]
  (try
    (let [period (t/new-duration (Integer/parseInt amount) (keyword unit))]
      (htstatus/ok
       (sync-changes period)))
    (catch AssertionError e
      (htstatus/bad-request params))
    (catch NumberFormatException e
      (htstatus/bad-request params))))

(def ^:private chown-chmod-xquery-template
  "[article-path user password resource permissions group]"
  (-> "chown-chmod.xq" io/resource (slurp :encoding "UTF-8")))

(defn chown-chmod [id user password]
  (->> (format chown-chmod-xquery-template
               articles-path
               user
               password
               (str/join "/" [articles-path id])
               "rw-rw-r--"
               "Neuartikel")
       (xquery)))

(defn create-article [id xml user password]
  (req {:method :put
        :url (id->uri id)
        :content-type :xml
        :body xml
        :basic-auth [user password]})
  (chown-chmod id user password))

(comment
  (mount/start #'short-exist->git)
  (mount/stop)
  (take 10 (articles))
  (articles->changeset (articles) (t/new-duration 2 :hours))
  (time (short-term-sync))
  (handle-period-sync {:path-params {:amount "1" :unit "hours"}}))
