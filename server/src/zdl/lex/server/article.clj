(ns zdl.lex.server.article
  (:require [byte-streams :as bs]
            [clojure.data.xml :as dx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.walk :refer [postwalk]]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [ring.util.request :as htreq]
            [zdl.lex.article :as article]
            [zdl.lex.article.fs :as afs]
            [zdl.lex.article.xml :as axml]
            [zdl.lex.fs :refer [file]]
            [zdl.lex.server.auth :as auth]
            [zdl.lex.server.git :as git]
            [zdl.lex.server.lock :as lock]
            [zdl.lex.server.solr.client :as solr-client]
            [zdl.lex.timestamp :as ts])
  (:import java.io.File
           [java.text Normalizer Normalizer$Form]
           java.time.Duration
           java.util.Date))

(def events
  (bus/event-bus))

(defn describe-git-file
  [f]
  (article/describe-article-file git/dir f))

(defn git-file->articles
  [f]
  (article/extract-articles (describe-git-file f)))

(defn git-file-updated!
  [f & {:keys [event] :or {event :updated}}]
  (->
   (d/future (git-file->articles f))
   (d/chain
    (fn [articles]
      (s/consume-async #(bus/publish! events event %) articles)))
   (d/catch
     (fn [e]
       (log/warnf e "Error while updating %s" f)
       (d/success-deferred true)))))

(defn git-file-removed!
  [f]
  (try
    (bus/publish! events :removed (describe-git-file f))
    (catch Throwable t
      (log/warnf t "Error while removing %s" f)
      (d/success-deferred true))))

(defn refresh-articles!
  []
  (let [threshold (Date.)]
    (->
     (s/consume-async #(git-file-updated! % :event :refreshed)
                      (afs/files git/dir))
     (d/chain
      (fn [_]
        (bus/publish! events :purge threshold))
      (fn [_]
        (let [duration (- (System/currentTimeMillis) (.getTime threshold))
              duration (Duration/ofMillis duration)]
          (log/infof "Refreshed articles in %s" duration)
          (d/success-deferred true))))
     (d/catch
         (fn [e]
           (log/warnf e "Error while refreshing articles")
           (d/success-deferred true))))))

(defstate git->article-changes
  :start (let [git-updates  (bus/subscribe git/events :updated)
               git-removals (bus/subscribe git/events :removed)]
           (s/consume-async git-file-updated! git-updates)
           (s/consume-async git-file-removed! git-removals)
           [git-updates git-removals])
  :stop (doseq [s git->article-changes]
          (s/close! s)))

(defstate article-changes->log
  :start (let [updates (bus/subscribe events :updated)
               removals (bus/subscribe events :removed)
               purges (bus/subscribe events :purge)]
           (s/consume-async
            (fn [article]
              (log/debugf
               "U %s"
               (select-keys article [:id :anchors :source :status]))
              (d/success-deferred true))
            updates)
           (s/consume-async
            (fn [article]
              (log/debugf "D %s" (select-keys article [:id]))
              (d/success-deferred true))
            removals)
           (s/consume-async
            (fn [threshold]
              (log/debugf "! Purging articles before %s" threshold)
              (d/success-deferred true))
            purges)
           [updates removals purges])
  :stop (doseq [s article-changes->log]
          (s/close! s)))

(def xml-template
  (axml/read-xml (io/resource "template.xml")))

(dx/alias-uri :dwds "http://www.dwds.de/ns/1.0")
(dx/alias-uri :xxml "http://www.w3.org/XML/1998/namespace")

(def article-namespaces
  {:xmlns "http://www.dwds.de/ns/1.0"})

(defn new-article-xml
  [xml-id form pos author]
  (dx/emit-str
   (postwalk
    (fn [node]
      (if (map? node)
        (condp = (node :tag)
          ::dwds/DWDS       (update node :attrs merge article-namespaces)
          ::dwds/Artikel    (let [ts (ts/format (java.time.LocalDate/now))]
                              (update node :attrs merge
                                      {::xxml/id         xml-id
                                       :Zeitstempel      ts
                                       :Erstellungsdatum ts
                                       :Autor            author}))
          ::dwds/Schreibung (assoc node :content (list form))
          ::dwds/Wortklasse (assoc node :content (list pos))
          node)
        node))
    xml-template)))

(defn form->filename [form]
  (-> form
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"\p{InCombiningDiacriticalMarks}" "")
      (str/replace "ß" "ss")
      (str/replace " " "_")
      (str/replace #"[^\p{Alpha}\p{Digit}\-_]" "_")))

(def ^:private new-article-collection "Neuartikel")

(defn get-article
  [{{{:keys [resource]} :path} :parameters}]
  (let [^File f (file git/dir resource)]
    (if (.isFile f)
      {:status 200 :body f}
      {:status 404 :body resource})))

(defn generate-id*
  []
  (str "E_" (rand-int 10000000)))

(defn generate-id
  []
  (d/loop [n 0]
    (if (< n 10)
      (let [id (generate-id*)]
        (d/chain
         (solr-client/id-exists? id)
         (fn [id-exists?] (if id-exists? (d/recur (inc n)) id))))
      (d/error-deferred
       (ex-info "Maximum number of article id generations exceeded" {:n n})))))

(defn create-article
  [{{:keys [user]} ::auth/identity {{:keys [form pos]} :query} :parameters}]
  (git/lock!)
  (->
   (generate-id)
   (d/chain
    (fn [xml-id]
      (let [xml      (new-article-xml xml-id form pos user)
            filename (form->filename form)
            id       (str new-article-collection "/" filename "-" xml-id ".xml")
            f        (file git/dir id)]
        (doto (.getParentFile f) (.mkdirs))
        (spit f xml :encoding "UTF-8")
        (git/add! f :lock? false)
        (git-file-updated! f)
        {:status 200 :body {:id id :form form :pos pos}})))
   (d/finally #(git/unlock!))))

(defn post-article
  [{{{:keys [resource]} :path} :parameters :keys [body] :as req}]
  (git/with-lock
    (let [^File f (file git/dir resource)]
      (if (.isFile f)
        (do
          (with-open [is (bs/to-input-stream body)
                      os (io/output-stream f)]
            (io/copy is os))
          (git-file-updated! f)
          {:status 200 :body f})
        {:status 404 :body resource}))))
