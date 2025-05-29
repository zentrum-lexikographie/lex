(ns zdl.lex.client
  (:require
   [clj-http.client :as http]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [lambdaisland.uri :as uri]
   [taoensso.telemere :as tm]
   [tick.core :as t]
   [zdl.lex.article :as article]
   [zdl.lex.env :as env]
   [zdl.lex.article.qa :as qa])
  (:import
   (java.io ByteArrayInputStream PushbackReader)
   (java.net URL)
   (java.util UUID)
   (ro.sync.exml.plugin.lock LockException)))

(def auth
  (atom env/server-auth))

(defn agent*
  [state]
  (agent state :error-handler (fn [_ t] (tm/error! t) nil)))

(def active-article
  (atom nil))

(def articles
  (agent* {}))

(def queries
  (agent* []))

(def issues
  (agent* {}))

(def links
  (agent* {}))

(def server-base
  (uri/uri env/server-url))

(def url-base
  (assoc server-base :scheme "lex"))

(defn lex?
  [uri]
  (-> uri :scheme (= "lex")))

(defn id->url
  [id]
  (uri/join url-base id))

(defn url->id
  [uri]
  (let [uri (uri/uri uri)]
    (when (lex? uri) (-> uri :path (str/replace #"^/" "")))))

(comment
  (-> "WDG/ve/Verfasserkollektiv-E_k_6565.xml" id->url url->id)
  (id->url "test.xml"))

(def login-url
  (str (uri/join env/server-url "status")))

(defn http-authenticate
  []
  (or
   @auth
   (let [status-con (.. (URL. login-url) (openConnection))]
     (.. status-con (setRequestProperty "Accept" "application/edn"))
     (with-open [status-stream (.. status-con (getInputStream))
                 status-reader (io/reader status-stream :encoding "UTF-8")
                 status-reader (PushbackReader. status-reader)]
       (let [{:keys [user password]} (edn/read status-reader)]
         (when (and user password)
           (reset! auth [user password])))))))

(defn handle-http-locked-response
  [{:keys [status body] :as response}]
  (when (= 423 status)
    (let [resource (body :resource)
          expires  (body :expires)
          owner    (body :owner)
          message  (str/join
                    \newline
                    ["Artikel gesperrt"
                     ""
                     (format "Pfad: %s" (or (not-empty resource) "<alle>"))
                     (format "Von: %s" owner)
                     (format "Ablaufdatum: %s"
                             (->> (t/instant expires)
                                  (t/zoned-date-time)
                                  (t/format "dd.MM.YYYY', 'HH:mm' Uhr'")))
                     ""])]
      (throw
       (doto (LockException. message true message) (.setOwnerName owner)))))
  response)

(def lock-token
  (-> (UUID/randomUUID) str str/lower-case))

(defn http-request
  [req & {:keys [lock?]}]
  (let [auth (http-authenticate)]
    (->
     req
     (update :method #(or % :get))
     (update :url #(str (uri/join env/server-url %)))
     (update :as #(or % :clojure))
     (update-in [:headers "Accept"] #(or % "application/edn"))
     (update :unexceptional-status #(conj (or % #{200}) 423))
     (cond-> auth (assoc :basic-auth auth))
     (cond-> lock? (assoc-in [:query-params :token] lock-token))
     (http/request)
     (handle-http-locked-response))))

(defn http-lock
  [id timeout]
  (->
   {:method       :post
    :url          (uri/join "lock/" id)
    :query-params {:ttl (str timeout)}}
   (http-request :lock? true)))

(defn http-unlock
  [id]
  (->
   {:method               :delete
    :url                  (uri/join "lock/" id)
    :unexceptional-status #{200 404}}
   (http-request :lock? true)))

(defn http-response->input-stream
  [{:keys [body]}]
  (ByteArrayInputStream. (.getBytes ^String body "UTF-8")))

(defn http-get-article
  [id]
  (-> {:method  :get
       :url     (uri/join "article/" id)
       :headers {"Accept" "text/xml, application/edn"}
       :as      "UTF-8"}
      (http-request :lock? true)
      (http-response->input-stream)))

(defn http-post-article
  [id xml-bytes]
  (-> {:method  :post
       :url     (uri/join "article/" id)
       :headers {"Content-Type" "text/xml"
                 "Accept"       "text/xml, application/edn"}
       :as      "UTF-8"
       :body    xml-bytes}
      (http-request :lock? true)
      (http-response->input-stream)))

(defn http-create-article
  [form pos]
  (->
   {:method       :put
    :url          "article/"
    :query-params {:form form
                   :pos  pos}}
   (http-request)
   (get-in [:headers "X-Lex-ID"])))

(defn http-suggest
  [q]
  (->
   {:method       :get
    :url          "index/suggest"
    :query-params {:q q}}
   (http-request)
   (get-in [:body :result])))

(defn http-search
  [q]
  (->
   {:url          "index"
    :query-params {:q     q
                   :limit "1000"}}
   (http-request)
   (get :body)))

(defn http-export-to-file
  [query csv-file]
  (->
   {:method       :get
    :url          "index/export"
    :as           :input-stream
    :query-params {:q     query
                   :limit 50000}}
   (http-request)
   (get :body)
   (io/copy csv-file)))

(defn http-get-issues
  [forms]
  (->
   {:url          "mantis"
    :query-params {:q forms}}
   (http-request)
   (get-in [:body :result])))

(defn http-get-links
  [anchors links]
  (->
   {:url          "index/links"
    :query-params (cond-> {}
                    (seq anchors) (assoc :links (seq anchors))
                    (seq links)   (assoc :anchors (seq links)))}
   (http-request)
   (get-in [:body :result])))

(defn query
  [q]
  (send-off queries
            (fn [queries]
              (let [timestamp (t/date-time)
                    result    (http-search q)]
                (->> queries
                     (remove (comp (partial = q) :query))
                     (take 9)
                     (cons (assoc result
                                  :id (UUID/randomUUID)
                                  :query q
                                  :timestamp timestamp))
                     (into []))))))

(defn get-issues
  [article]
  (some-> article :forms http-get-issues))

(defn assoc-direction
  [anchors links link]
  (assoc link
         :incoming? (some anchors (:links link))
         :outgoing? (some links (:anchors link))))

(defn get-links
  [article id]
  (let [anchors (into #{} (:anchors article))
        links   (into #{} (map :anchor) (:links article))]
    (when (or (seq anchors) (seq links))
      (try
        (let [result  (sequence
                       (comp (remove (comp #{id} :id))
                             (map (partial assoc-direction anchors links)))
                       (http-get-links anchors links))
              anchors (into #{} (mapcat :anchors) result)
              missing (into #{} (remove anchors) links)]
          {:links   (vec (sort-by (comp article/collation-key :form) result))
           :missing (vec (sort-by article/collation-key missing))})
        (catch Throwable t (tm/error! t) nil)))))

(defn xml->article
  [_ id xml-stream-fn]
  (tm/with-ctx+ {::id id}
    (try
      (with-open [is (xml-stream-fn)]
        (let [xml     (article/read-xml is)
              article (article/metadata xml)
              links   (future (get-links article id))
              issues  (future (get-issues article))
              errors  (future (qa/check-typography xml))]
          (assoc article
                 ::xml xml
                 ::links @links
                 ::issues @issues
                 ::qa @errors)))
      (catch Throwable t (tm/error! t) nil))))

(defn update-article
  [id xml-stream-fn]
  (send-off articles update id xml->article id xml-stream-fn))

(defn dissoc-article
  [id]
  (send articles dissoc id))
