(ns zdl.lex.client
  (:require
   [chime.core :as chime]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hato.client :as hc]
   [hato.websocket :as ws]
   [integrant.core :as ig]
   [lambdaisland.uri :as uri]
   [nrepl.server :as repl]
   [taoensso.telemere :as tel]
   [tick.core :as t]
   [zdl.lex.article :as article]
   [zdl.lex.article.typography :as article.typography]
   [zdl.lex.auth :as auth]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.util :refer [pr-edn-str]]
   [seesaw.bind :as uib])
  (:import
   (java.io ByteArrayInputStream)
   (java.net Authenticator)
   (java.util UUID)
   (ro.sync.exml.plugin.lock LockException)))

(def config
  (let [repl-port (some->> (getenv "REPL_PORT") parse-long)]
    (cond-> {::socket {}} repl-port (assoc ::repl {:port repl-port}))))

(def active-user
  (atom nil))

(def id
  (let [sys-prop #(System/getProperty %)]
    (atom {:client-id    (str (random-uuid))
           :java-version (sys-prop "java.version")
           :os-name      (sys-prop "os.name")
           :os-version   (sys-prop "os.version")})))

(uib/bind
 active-user
 (uib/filter some?)
 (uib/b-do* #(swap! id assoc :user %)))

(def server-url
  (getenv "SERVER_URL" "http://localhost:3000"))

(def ws-url
  (-> (uri/uri server-url)
      (update :scheme {"http" "ws" "https" "wss"})
      (uri/join "socket") (str)))

(def http-client
  (delay
    (hc/build-http-client
     {:authenticator (or (auth/create-authenticator (getenv "SERVER_USER")
                                                    (getenv "SERVER_PASSWORD"))
                         (Authenticator/getDefault))
      :version       :http-1.1})))

(def active-article
  (atom nil))

(defn agent*
  [state]
  (agent state :error-handler (fn [_ t] (tel/error! ::agent t) nil)))

(def articles
  (agent* {}))

(def queries
  (agent* []))

(def issues
  (agent* {}))

(def links
  (agent* {}))

(def gpt-chat
  (agent* {:persona "Du bist ein Lexikograph und gibst kurze, genaue Antworten!"
           :history []
           :prompt nil}))

(defn lex?
  [uri]
  (-> uri :scheme (= "lex")))

(def url-base
  (assoc (uri/uri server-url) :scheme "lex"))

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

(defn handle-auth-context
  [{{user "x-lex-user"} :headers :as response}]
  (when-not @active-user (reset! active-user user))
  response)

(defn handle-http-errors
  [{:keys [status] :as response}]
  (when-not (#{200 423} status) (throw (ex-info "HTTP error" response)))
  response)

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
  (->
   req
   (assoc :http-client @http-client :throw-exceptions? false)
   (update :method #(or % :get))
   (update :url #(str (uri/join server-url %)))
   (update-in [:headers "Accept"] #(or % "application/edn"))
   (update :as #(or % :clojure))
   (cond-> lock? (assoc-in [:query-params :token] lock-token))
   (hc/request)
   (handle-http-errors)
   (handle-auth-context)
   (handle-http-locked-response)))


(defn http-lock
  [id timeout]
  (->
   {:method       :post
    :url          (uri/join "lock/" id)
    :query-params {:ttl (str timeout)}}
   (http-request :lock? true)))

(defn http-unlock
  [id]
  (try
    (->
     {:method :delete
      :url    (uri/join "lock/" id)}
     (http-request :lock? true))
    (catch Throwable t
      (when (not= 404 (-> t ex-data :status))
        (tel/with-ctx+ {::id id} (tel/error! ::unlock t))))))

(defn http-response->input-stream
  [{:keys [body]}]
  (ByteArrayInputStream. body))

(defn http-get-article
  [id]
  (-> {:method  :get
       :url     (uri/join "git/" id)
       :headers {"Accept" "text/xml, application/edn"}
       :as      :byte-array}
      (http-request :lock? true)
      (http-response->input-stream)))

(defn http-post-article
  [id xml-bytes]
  (-> {:method  :post
       :url     (uri/join "git/" id)
       :headers {"Content-Type" "text/xml"
                 "Accept"       "text/xml, application/edn"}
       :as      :byte-array
       :body    xml-bytes}
      (http-request :lock? true)
      (http-response->input-stream)))

(defn http-create-article
  [form pos]
  (->
   {:method       :put
    :url          "git/"
    :query-params {:form form
                   :pos  pos}
    :as           :byte-array}
   (http-request)
   (get-in [:headers "x-lex-id"])))

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
    :as           :stream
    :query-params {:q     query
                   :limit 50000}}
   (http-request)
   (get :body)
   (io/copy csv-file)))

(defn http-get-issues
  [forms]
  (->
   {:url          "index/issues"
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
        (catch Throwable t (tel/error! ::links t) nil)))))

(defn xml->article
  [_ id xml-stream-fn]
  (tel/with-ctx+ {::id id}
    (try
      (with-open [is (xml-stream-fn)]
        (let [xml     (article/read-xml is)
              article (article/metadata xml)
              links   (future (get-links article id))
              issues  (future (get-issues article))
              errors  (future (article.typography/check xml))]
          (assoc article
                 ::xml xml
                 ::links @links
                 ::issues @issues
                 ::qa @errors)))
      (catch Throwable t (tel/error! ::article-xml t) nil))))

(defn update-article
  [id xml-stream-fn]
  (send-off articles update id xml->article id xml-stream-fn))

(defn dissoc-article
  [id]
  (send articles dissoc id))

(def socket
  (atom nil))

(defn close-socket!
  []
  (try
    (some-> @socket (ws/close!) (deref))
    (catch Throwable t
      (tel/error! ::socket-close t))
    (finally
      (reset! socket nil))))

(defmulti socket-message-received :content-type)

(defmethod socket-message-received :gpt
  [{{[{message "message"}] "choices"} :content}]
  (send gpt-chat
        (fn [{:keys [prompt] :as gpt-chat}]
          (-> gpt-chat
              (update :history conj {"role" "user" "content" prompt} message)
              (assoc :prompt nil)))))

(defmethod socket-message-received :default
  [message]
  (tel/with-ctx+ {::message message} (tel/event! ::socket-message :info)))

(defn open-socket!
  []
  (or
   @socket
   (->>
    {:on-message  (fn [_ws ^java.nio.CharBuffer msg last?]
                    ;; FIXME: handle `last?`, concatenating chunks of
                    ;; larger messages
                    (assert (true? last?))
                    (socket-message-received (read-string (str msg))))
     :on-error    (fn [_ws error]
                    (tel/error! ::socket-error error)
                    (close-socket!))
     :on-close    (fn [_ws _status _reason]
                    (tel/event! ::socket-closing :info))
     :headers     {"X-Lex-Client-Id" (@id :client-id)}
     :http-client @http-client}
    (ws/websocket ws-url)
    (deref)
    (reset! socket))))

(defn send->socket
  [content-type content]
  (let [data (assoc @id :content-type content-type :content content)]
    (tel/with-ctx+ data
      (if-let [ws @socket]
        (locking ws
          (try
            @(ws/send! ws (pr-edn-str data))
            (catch Throwable t
              (tel/error! ::socket-send t)
              (close-socket!))))
        (tel/event! ::send-failed :error)))))

(defn gpt-request->socket
  [{:keys [history] :as gpt-chat} prompt persona]
  (let [messages (cond->> (conj history {"role" "user" "content" prompt})
                   persona (cons {"role" "system" "content" persona}))]
    (send->socket :gpt {"messages" (vec messages)})
    (assoc gpt-chat :prompt prompt :persona persona)))

(defmethod ig/init-key ::socket
  [_ _]
  (chime/chime-at
   (chime/periodic-seq (t/instant) (t/of-seconds 5))
   (fn [_] (when @active-user (open-socket!) (send->socket :ping :ping)))
   {:error-handler (fn [e]
                     (tel/error! ::socket-keep-alive e)
                     (not (instance? InterruptedException e)))}))

(defmethod ig/halt-key! ::socket
  [_ connection]
  (.close connection)
  (close-socket!))

(defmethod ig/init-key ::repl
  [_ {:keys [port]}]
  (repl/start-server :port port))

(defmethod ig/halt-key! ::repl
  [_ server]
  (repl/stop-server server))
