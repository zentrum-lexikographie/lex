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
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]
            [zdl-lex-common.xml :as xml]
            [mount.core :as mount])
  (:import java.net.URI
           java.text.Normalizer
           java.text.Normalizer$Form))

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

(defn articles []
  (->>
   (for [doc (-> (xquery articles-xquery) (xml/parse) (article-docs))]
     {:id (article-doc-uri doc)
      :modified (article-doc-modified doc)})
   (remove (comp #{"indexedvalues.xml"} :id))))

(defn articles->changeset [articles change-period]
  (let [existing-ids (->> articles (map :id) (into #{}))
        removed (->> (store/article-files)
                     (map store/file->id)
                     (remove existing-ids))
        added (->> (map store/id->file existing-ids)
                   (remove fs/exists?)
                   (map store/file->id))
        added-ids (into #{} added)
        change-threshold (t/- (t/now) change-period)
        changed (->> articles
                     (filter (comp (partial t/< change-threshold) :modified))
                     (map :id)
                     (remove added-ids))]
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
  :stop (async/close! short-exist->git))

(defstate long-exist->git
  :start (cron/schedule "0 0 1 * * ?" "eXist-db Sync. (last days)"
                        long-term-sync)
  :stop (async/close! long-exist->git))

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

(def xml-template (slurp (io/resource "template.xml") :encoding "UTF-8"))

(defn generate-id []
  (let [candidate #(str "E_" (rand-int 10000000))]
    (loop [id (candidate)]
      (if-not (solr/id-exists? id) id
              (recur (candidate))))))

(defn form->filename [form]
  (-> form
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"\p{InCombiningDiacriticalMarks}" "")
      (str/replace "ß" "ss")
      (str/replace " " "-")
      (str/replace #"[^\p{Alpha}\p{Digit}\-]" "_")))

(def ^:private new-article-collection "Neuartikel-004")

(defn new-article [form pos author]
  (let [xml-id (generate-id)
        filename (form->filename form)
        id (str new-article-collection "/" filename "-" xml-id ".xml")
        doc (xml/parse xml-template)
        element-by-name #(-> (.getElementsByTagName doc %) xml/nodes->seq first)]
    (doto (element-by-name "Artikel")
      (.setAttribute "xml:id" xml-id)
      (.setAttribute "Zeitstempel" (t/format :iso-local-date (t/date)))
      (.setAttribute "Autor" author))
    (.. (element-by-name "Schreibung") (setTextContent form))
    (.. (element-by-name "Wortklasse") (setTextContent pos))
    [id (xml/serialize doc)]))

(comment
  (new-article "testen" "Verb" "middell")
  (mount/start #'short-exist->git)
  (mount/stop)
  (take 10 (articles))
  (articles->changeset (articles) (t/new-duration 1 :hours))
  (time (short-term-sync))
  (handle-period-sync {:path-params {:amount "1" :unit "hours"}}))
