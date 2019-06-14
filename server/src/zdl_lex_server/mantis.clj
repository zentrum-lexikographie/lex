(ns zdl-lex-server.mantis
  (:require [clj-soap.client :as soap]
            [clojure.core.async :as async]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-server.cron :as cron]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.store :as store]))

(def client-instance (atom nil))

(defn client []
  (swap!
   client-instance
   (fn [instance]
     (or instance
         (let [mantis-base (config :mantis-url)
               wsdl (str mantis-base "/api/soap/mantisconnect.wsdl")
               endpoint (str mantis-base "/api/soap/mantisconnect.php")]
           (soap/client-fn {:wsdl wsdl :options {:endpoint-url endpoint}}))))))

(def ^:private authenticate
  (partial merge {:username (config :mantis-user)
                  :password (config :mantis-password)}))

(def ^:private project (config :mantis-project))

(defn- zip->items [loc]
  (zx/xml-> loc dz/children zip/branch? :return :item))

(defn- results
  ([op] (results op {}))
  ([op params] (results op params zip->items))
  ([op params zip->locs]
   (timbre/info {:op op :params (dissoc params :username :password)})
   (-> ((client) op (authenticate params))
       (zip/xml-zip)
       (zip->locs))))

(defn scroll
  ([op] (scroll op {}))
  ([op params] (scroll op params zip->items))
  ([op params zip->locs] (scroll op params zip->locs 1000))
  ([op params zip->locs page-size] (scroll op params zip->locs page-size 0))
  ([op params zip->locs page-size page]
   (let [params (merge params {:page_number page :per_page page-size})
         results (seq (results op params zip->locs))]
     (if results
       (concat results
               (lazy-seq (scroll op params zip->locs page-size (inc page))))))))

(defn- property [loc k]
  (zx/xml1-> loc k zx/text))

(defn mantis-enum
  ([op] (mantis-enum op {}))
  ([op params]
   (into {}
         (for [item (results op params)]
           (vector (property item :id)
                   (property item :name))))))

(def ^:private summary->lemma
  (comp #(or % "-") second first (partial re-seq #"([^\-\s]+) --")))

(defn issues []
  (let [status (mantis-enum :mc_enum_status)
        severities (mantis-enum :mc_enum_severities)
        resolutions (mantis-enum :mc_enum_resolutions)
        users (mantis-enum :mc_project_get_users {:project_id project :access 0})]
    (->> (scroll :mc_project_get_issue_headers {:project_id project})
         (map (fn [item]
                (let [summary (property item :summary)]
                  {:id (-> (property item :id) Integer/parseInt)
                   :category (property item :category)
                   :summary summary
                   :lemma (summary->lemma summary)
                   :last-updated (property item :last_updated)
                   :status (-> (property item :status) status)
                   :severity (-> (property item :severity) severities)
                   :reporter (-> (property item :reporter) users)
                   :handler (-> (property item :handler) users)
                   :resolution (-> (property item :resolution) resolutions)
                   :attachments (-> (property item :attachments_count) Integer/parseInt)
                   :notes (-> (property item :notes_count) Integer/parseInt)}))))))

(defn ->dump [data]
  (let [data (->> data
                  (group-by :id) (vals) (map first)
                  (sort-by :last-updated #(compare %2 %1))
                  (vec))]
    (spit store/mantis-dump (pr-str data))
    data))

(defn dump-> []
  (try (read-string (slurp store/mantis-dump))
       (catch Throwable t (timbre/warn t) [])))

(defonce index (atom nil))

(defn ->index [issues]
  (reset! index (group-by :lemma issues)))

(reset! index (-> (dump->) ->index))

(defstate issues->dump->index
  "Synchronizes Mantis issues"
  :start (let [schedule (cron/parse "0 */15 * * * ?")
               ch (async/chan)]
           (async/go-loop []
             (when (async/alt! (async/timeout (cron/millis-to-next schedule)) :tick
                               ch ([v] v))
               (async/<!
                (async/thread
                  (try (-> (issues) ->dump ->index)
                       (catch Throwable t (timbre/warn t) {}))))
               (recur)))
           ch)
  :stop (async/close! issues->dump->index))

(defn handle-issue-lookup [{{:keys [lemma]} :path-params}]
  (htstatus/ok
   (or (@index lemma) [])))

(comment
  store/mantis-dump
  @client-instance
  (-> (dump->) (->dump) last)
  (-> (issues) ->dump ->index last)
  (-> @index keys sort)
  (handle-issue-lookup {:path-params {:lemma "Strafzettel"}}))
