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
            [zdl-lex-server.xml :as xml]))

(def ^:private req
  (comp #(timbre/spy :trace %)
        #(dissoc % :http-client)
        http/request
        (partial merge (config :exist-req))
        #(timbre/spy :trace %)))

(def ^:private url (partial str (config :exist-base)))

(def ^:private articles-path "/db/dwdswb/data")
(def ^:private articles-path-prefix (re-pattern (str "^" articles-path  "/")))

(defn- id->uri [id]
  (str articles-path "/" id))

(defn- uri->id [uri]
  (str/replace uri articles-path-prefix ""))

(defn copy [id]
  (let [store-file (store/id->file id)
        exist-xml-req {:method :get
                       :url (url "/webdav" articles-path "/" id)
                       :as :stream}]
    (with-open [exist-xml (-> exist-xml-req req :body)]
      (locking store/git-dir
        (-> store-file fs/parent fs/mkdirs)
        (io/copy exist-xml store-file)
        store-file))))

(def ex-ns "http://exist.sourceforge.net/NS/exist")

(defn xquery
  ([q] (xquery articles-path q))
  ([path q]
   (let [query-doc (xml/new-document)
         element #(.createElementNS query-doc ex-ns %)
         xml-body (-> (.appendChild query-doc
                                    (doto (element "ex:query")
                                      (.setAttribute "max" "1000000")
                                      (.setAttribute "cache" "no")))
                      (.appendChild (element "ex:text"))
                      (.appendChild (.createCDATASection query-doc q))
                      (.getOwnerDocument)
                      (xml/doc-str))
         xml-req {:method :post
                   :url (url "/rest" path)
                   :content-type :xml
                   :body xml-body}]
     (-> xml-req req :body))))

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
  (comp str (xml/xpath-fn "uri/text()")))

(def ^:private article-doc-modified
  (comp t/instant t/parse str (xml/xpath-fn "modified/text()")))

(defn articles []
  (->>
   (for [doc (-> (xquery articles-xquery) (xml/parse-str) (article-docs))]
     {:id (uri->id (article-doc-uri doc))
      :modified (article-doc-modified doc)})
   (remove (comp #{"indexedvalues.xml"} :id))))

(defn changed-articles [duration]
  (let [threshold (t/- (t/now) duration)]
    (->> (articles)
         (filter (comp (partial t/< threshold) :modified))
         (map :id))))

(comment
  (take 10 (articles))
  (time (take 5 (changed-articles (t/new-duration 7 :days))))
  (doseq [id (changed-articles (t/new-duration 7 :days))]
    (async/>!! exist->git id)))

(defstate exist->git
  "Synchronizes articles in exist-db with git"
  :start (let [ch (async/chan 1000)]
           (async/go-loop []
             (when-let [article (async/<! ch)]
               (timbre/trace {:exist article})
               (async/<!
                (async/thread
                  (try
                    (copy article)
                    (catch Throwable t (timbre/warn t)))))
               (recur)))
           ch)
  :stop (async/close! exist->git))

(defstate changed-in-exist->git
  "Synchronizes changed articles in exist-db with git"
  :start (let [schedule (cron/parse "0 */15 * * * ?")
               since (t/new-duration 1 :hours)
               ch (async/chan)]
           (async/go-loop []
             (when (async/alt! (async/timeout (cron/millis-to-next schedule)) :tick
                               ch ([v] v))
               (doseq [article (async/<!
                                (async/thread
                                  (try
                                    (changed-articles since)
                                    (catch Throwable t
                                      (timbre/warn t)
                                      []))))]
                 (async/>! exist->git article))
               (recur)))
           ch)
  :stop (async/close! changed-in-exist->git))

(defn handle-article-sync [{{:keys [id]} :params}]
  (htstatus/ok
   {id (async/>!! exist->git id)}))

(defn handle-period-sync [{{:keys [amount unit] :as params} :path-params}]
  (try
    (let [since (t/new-duration (Integer/parseInt amount) (keyword unit))
          articles (changed-articles since)]
      (doseq [article articles]
        (async/>!! exist->git article))
      (htstatus/ok
       {:changed articles}))
    (catch AssertionError e
      (htstatus/bad-request params))
    (catch NumberFormatException e
      (htstatus/bad-request params))))

(comment
  (handle-period-sync {:path-params {:amount "7" :unit "days"}}))
