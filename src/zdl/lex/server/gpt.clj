(ns zdl.lex.server.gpt
  (:require
   [clojure.core.async :as a]
   [clojure.tools.logging :as log]
   [jsonista.core :as json]
   [lambdaisland.uri :as uri]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.http :as http]))

(def server-url
  (getenv "GPT_SERVER_URL" "http://localhost:8000/v1/"))

(def auth-token
  (getenv "GPT_SERVER_AUTH_TOKEN"))

(def chat-completion-base-request
  {:method  :post
   :url     (str (uri/join server-url "chat/completions"))
   :headers (cond-> {"Content-Type" "application/json"
                     "Accept"       "application/json"}
              auth-token (assoc "Authorization" (str "Bearer " auth-token)))})

(defn chat-completion-request
  [user-message]
  (as-> user-message $
    {:messages    [{:role    "system"
                    :content "Du bist ein hilfreicher Assistent."}
                   {:role    "user"
                    :content $}]
     :max_tokens  0
     :temperature 0.0
     :top_p       0.75}
    (json/write-value-as-string $ json/keyword-keys-object-mapper)
    (assoc chat-completion-base-request :body $)))

(defn chat-completion-response
  [{{[response] :choices} :body :keys [request-time]}]
  (let [message (get-in response [:message :content])]
    (-> response
        (assoc :message message :request-time request-time)
        (dissoc :index)
        (update :finish_reason keyword))))

(defn log-definition
  [{:keys [lemma message request-time] :as definition}]
  (log/infof "[%03.2f s] '%s': %s" (float (/ request-time 1000)) lemma message)
  definition)

(defn chat-completion-error
  [req error]
  (log/warnf error "Error while serving definition request %s" req)
  error)

(def definition-requests
  (a/chan (a/dropping-buffer 32)))

(def definition-responses
  (let [responses (a/chan)]
    (a/go-loop []
      (when-let [{:keys [lemma] :as req} (a/<! definition-requests)]
        (try
          (let [[response error] (-> (format "Was bedeutet \"%s\"?" lemma)
                                     (chat-completion-request)
                                     (http/async-request))]
            (->>
             (a/alt! response ([response] (-> response
                                              (http/read-json)
                                              (chat-completion-response)
                                              (merge req)
                                              (log-definition)))
                     error    ([error] (assoc req :error error)))
             (a/>! responses)))
          (catch Throwable t
            (log/warnf t "Error while GPT-defining '%s'" lemma)
            (a/>! responses (assoc req :error t)))))
      (recur))
    (a/pub responses :lemma)))

(def definition-timeout
  120000)

(defn handle-definitions
  [req]
  (let [lemma      (get-in req [:parameters :query :q])
        request    {:lemma lemma}
        definition (a/chan 1)
        timeout    (a/timeout definition-timeout)]
    (a/go
      (a/sub definition-responses lemma definition)
      (try
        (a/>! definition-requests request)
        (a/alt! definition ([{:keys [error] :as definition}]
                            (if error
                              (throw error)
                              {:status 200 :body definition}))
                timeout    ([_] {:status 404 :body request}))
        (catch Throwable t
          (log/warnf t "Error while defining '%s'" lemma)
          {:status 500 :body request})
        (finally
          (a/unsub definition-responses lemma definition))))))
