(ns zdl-lex-server.exist
  (:require [clj-http.client :as http]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-server.cron :as cron]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store]
            [zdl-lex-common.xml :as xml])
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
  (->
   (str/join
    "\n"
    ["xquery version '3.0';"
     "for $doc in fn:collection('%s')"
    "let $modified := xmldb:last-modified(util:collection-name($doc),util:document-name($doc))"
     "return (<doc><uri>{fn:document-uri($doc)}</uri><modified>{$modified}</modified></doc>)"])
   (format articles-path)))

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

(defn articles []
  (->>
   (for [doc (-> (xquery articles-xquery) (xml/parse) (article-docs))]
     {:id (article-doc-uri doc)
      :modified (article-doc-modified doc)})
   (remove (comp #{"indexedvalues.xml"} :id))))

(defn changed-articles [duration]
  (let [threshold (t/- (t/now) duration)]
    (->> (articles)
         (filter (comp (partial t/< threshold) :modified))
         (map :id))))

(defn removed-articles []
  (let [existing (->> (articles) (map :id) (into #{}))]
    (->> (store/article-files)
         (map store/file->id)
         (remove existing))))

(defn- sync-changed-articles
  ([] (sync-changed-articles (t/new-duration 1 :hours)))
  ([since]
   (->> (changed-articles since)
        (pmap copy)
        (doall))))

(defstate changed-in-exist->git
  "Synchronizes changed articles in exist-db with git"
  :start (cron/schedule
          "0 */15 * * * ?" "eXist-db Synchronization (mod)" sync-changed-articles)
  :stop (async/close! changed-in-exist->git))

(defn- sync-removed-articles []
  (->> (for [id (removed-articles)]
         [id (delete id)])
       (into {})))

(defstate removed-in-exist->git
  "Synchronizes removed articles in exist-db with git"
  :start (cron/schedule
          "0 10 * * * ?" "eXist-db Synchronization (del)" sync-removed-articles)
  :stop (async/close! changed-in-exist->git))

(defn handle-article-sync [{{:keys [id]} :params}]
  (htstatus/ok
   {id (copy id)}))

(defn handle-period-sync [{{:keys [amount unit] :as params} :path-params}]
  (try
    (let [since (t/new-duration (Integer/parseInt amount) (keyword unit))]
      (htstatus/ok
       (sync-changed-articles since)))
    (catch AssertionError e
      (htstatus/bad-request params))
    (catch NumberFormatException e
      (htstatus/bad-request params))))

(comment
  (take 10 (articles))
  (handle-period-sync {:path-params {:amount "1" :unit "hours"}}))
