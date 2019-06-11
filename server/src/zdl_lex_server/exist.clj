(ns zdl-lex-server.exist
  (:require [clj-http.client :as http]
            [clojure.core.async :as async]
            [clojure.data.xml :as xml]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-server.cron :as cron]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store]
            [zdl-lex-server.git :as git]))

(def ^:private req
  (comp #(timbre/spy :debug %)
        #(dissoc % :http-client)
        http/request
        (partial merge (config :exist-req))
        #(timbre/spy :debug %)))

(def ^:private url (partial str (config :exist-base)))

(def ^:private articles-path "/db/dwdswb/data")
(def ^:private articles-path-prefix (re-pattern (str "^" articles-path  "/")))

(defn- id->uri [id]
  (str articles-path "/" id))

(defn- uri->id [uri]
  (str/replace uri articles-path-prefix ""))

(xml/alias-uri 'ex "http://exist.sourceforge.net/NS/exist")

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

(defn xquery
  ([q] (xquery articles-path q))
  ([path q]
   (let [xml-body [::ex/query {:xmlns/ex "http://exist.sourceforge.net/NS/exist"
                               :max "1000000"
                               :cache "no"}
                   [::ex/text [:-cdata q]]]
         xml-body (-> xml-body
                      xml/sexp-as-element
                      xml/emit-str)
         xml-req {:method :post
                   :url (url "/rest" path)
                   :content-type :xml
                   :body xml-body}]
     (-> xml-req req :body))))

(def ^:private changed-articles-xquery
  (-> "changed-articles.xq" io/resource slurp))

(defn changed-articles [duration]
  (let [threshold (t/- (t/now) duration)
        q (format changed-articles-xquery threshold)]
    (->>
     (-> (xquery q)
         (xml/parse-str :namespace-aware true)
         (zip/xml-zip)
         (zx/xml-> dz/children zip/branch? :doc :uri zx/text))
     (map uri->id)
     (sort))))

(comment
  (doseq [id (changed-articles (t/new-duration 7 :days))]
    (async/>!! exist->git id)))

(defstate exist->git
  "Synchronizes articles in exist-db with git"
  :start (let [ch (async/chan 1000)]
           (async/go-loop []
             (when-let [article (async/<! ch)]
               (timbre/info {:exist article})
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
