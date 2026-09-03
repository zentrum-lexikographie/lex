(ns zdl.lex.gpt
  (:require
   [clojure.string :as str]
   [iapetos.core :as prometheus]
   [integrant.core :as ig]
   [jsonista.core :as json]
   [org.httpkit.client :as hc]
   [taoensso.telemere :as tel]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.metrics :as metrics]
   [zdl.lex.queue :as queue])
  (:import
   (org.httpkit.client ClientSslEngineFactory)))

(def gpt-api-url
  (getenv "GPT_API_URL" "https://zdl-gpu01.bbaw.de/api/"))

(def gpt-api-key
  (getenv "GPT_API_KEY" "abc123"))

(def gpt-model
  (getenv "MODEL" "llama33"))

(defn log-api-request
  [{:keys [error] :as response}]
  (tel/with-ctx+ {::gpt-api-response response}
    (if-not error
      (do (tel/event! ::gpt-api-tx :debug) response)
      (do (tel/error! ::gpt-api-error error) nil))))

(defn read-json-response
  [{{:keys [content-type]} :headers :as response}]
  (when response
    (cond-> response
      (str/includes? "application/json" (or content-type ""))
      (update :body json/read-value))))

(def default-callback
  (comp read-json-response log-api-request))

(defn gpt-api-request
  ([req]
   (gpt-api-request req default-callback))
  ([req f]
   (tel/with-ctx+ {::gpt-api-request req}
     (-> {:sslengine (ClientSslEngineFactory/trustAnybody) :keepalive -1}
         (merge req)
         (update :url (partial str gpt-api-url))
         (assoc-in [:headers "Authorization"] (str "Bearer " gpt-api-key))
         (hc/request #(tel/with-ctx+ {::gpt-api-request req} (f %)))))))

(defn models
  []
  (-> (gpt-api-request {:method :get :url "models"}) deref :body (get "data")))

(defn complete
  ([req]
   (complete req default-callback))
  ([req f]
  (-> {:method  :post
       :url     "chat/completions"
       :headers {"Content-Type" "application/json"}
       :body    (json/write-value-as-string (assoc req "model" gpt-model))}
      (gpt-api-request f))))

(defn qa
  [q]
  (->
   (complete {"messages" [{"role" "user" "content" q}]})
   deref (get-in [:body "choices" 0 "message" "content"])))

(defn echo
  [req]
  (json/write-value-as-bytes (json/read-value req)))

(defn handle
  [req]
  (prometheus/with-duration (metrics/registry :zdl_lex/gpt)
    (let [req  (json/read-value req)
          resp @(complete req)]
      (tel/with-ctx+ {::request req ::response resp}
        (tel/event! ::complete :debug)
        (when-let [error (resp :error)]
          (prometheus/inc metrics/registry :zdl_lex/errors {:source "gpt"})
          (throw (tel/error! ::completion-error error)))
        (.getBytes ^String (resp :body) "UTF-8")))))

(defn exit
  []
  (System/exit 1))

(def dev-config
  {::queue/rpc-server {:queue "gpt" :handle echo}})

(def main-config
  {::queue/rpc-server {:queue "gpt" :handle handle :on-cancel exit}})

(defn -main
  [& _]
  (let [system (ig/init main-config)]
    (. (Runtime/getRuntime) (addShutdownHook (Thread. #(ig/halt! system))))
    @(promise)))
