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

(def issue-id->url
  (partial str (config :mantis-url) "/view.php?id="))

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

(defn- zip->return [loc]
  (zx/xml-> loc dz/children zip/branch? :return))

(defn- zip->items [loc]
  (zx/xml-> loc dz/children zip/branch? :return :item))

(defn- results
  ([op] (results op {}))
  ([op params] (results op params zip->items))
  ([op params zip->locs]
   (timbre/trace {:op op :params (dissoc params :username :password)})
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

(defn- property [& path]
  (apply zx/xml1-> (concat path [zx/text])))

(def ^:private int-property
  (comp #(some-> % Integer/parseInt) property))

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
                (let [id (int-property item :id)
                      summary (property item :summary)]
                  {:id id
                   :url (issue-id->url id)
                   :category (property item :category)
                   :summary summary
                   :lemma (some-> summary summary->lemma)
                   :last-updated (property item :last_updated)
                   :status (some-> (property item :status) status)
                   :severity (some-> (property item :severity) severities)
                   :reporter (some-> (property item :reporter) users)
                   :handler (some-> (property item :handler) users)
                   :resolution (some-> (property item :resolution) resolutions)
                   :attachments (int-property  item :attachments_count)
                   :notes (int-property item :notes_count)}))))))

(defn issue [id]
  (->> (results :mc_issue_get {:issue_id id} zip->return)
       (map (fn [item]
              (let [id (int-property item :id)
                    summary (property item :summary)]
                {:id (int-property item :id)
                 :url (issue-id->url id)
                 :category (property item :category)
                 :summary summary
                 :lemma (some-> summary summary->lemma)
                 :last-updated (property item :last_updated)
                 :status (some-> (property item :status :name))
                 :severity (some-> (property item :severity :name))
                 :reporter (some-> (property item :reporter :name))
                 :handler (some-> (property item :handler :name))
                 :resolution (some-> (property item :resolution :name))
                 :attachments (some-> (zx/xml-> item :attachments) count)
                 :notes (some-> (zx/xml-> item :notes) count)})))
       (first)))

(defn store-dump [data]
  (let [data (->> data
                  (group-by :id) (vals) (map first)
                  (sort-by :last-updated #(compare %2 %1))
                  (vec))]
    (spit store/mantis-dump (pr-str data))
    data))

(defn read-dump []
  (try (read-string (slurp store/mantis-dump))
       (catch Throwable t (timbre/warn t) [])))

(defonce index (atom nil))

(defn index-issues [issues]
  (group-by :lemma issues))

(->> (read-dump) (index-issues) (reset! index))

(defstate issues->dump->index
  "Synchronizes Mantis issues"
  :start (let [schedule (cron/parse "0 */15 * * * ?")
               ch (async/chan)]
           (async/go-loop []
             (when (async/alt! (async/timeout (cron/millis-to-next schedule)) :tick
                               ch ([v] v))
               (async/<!
                (async/thread
                  (try (->> (issues) (store-dump) (index-issues) (reset! index))
                       (catch Throwable t (timbre/warn t) {}))))
               (recur)))
           ch)
  :stop (async/close! issues->dump->index))

(defn handle-issue-lookup [{{:keys [q]} :params}]
  (htstatus/ok
   (pmap (comp issue :id) (or (@index q) []))))

(comment
  store/mantis-dump
  @client-instance
  (-> (read-dump) (store-dump) last)
  (->> (issues) store-dump index-issues (reset! index) last)
  (-> @index keys sort)
  (handle-issue-lookup {:params {:q "Leder"}}))
