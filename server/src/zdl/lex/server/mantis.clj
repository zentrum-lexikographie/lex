(ns zdl.lex.server.mantis
  (:require [clj-soap.client :as soap]
            [clojure.core.async :as a]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.zip :as zip]
            [metrics.timers :refer [deftimer time!]]
            [mount.core :refer [defstate]]
            [ring.util.http-response :as htstatus]
            [zdl.lex.cron :as cron]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :as fs]))

(def mantis-base
  (delay (getenv "ZDL_LEX_MANTIS_BASE" "https://odo.dwds.de/mantis")))

(defn issue-id->url
  [id]
  (str @mantis-base "/view.php?id=" id))

(def client
  (delay
    (let [wsdl (str @mantis-base "/api/soap/mantisconnect.wsdl")
          endpoint (str @mantis-base "/api/soap/mantisconnect.php")]
      (soap/client-fn {:wsdl wsdl :options {:endpoint-url endpoint}}))))

(def authenticate
  (delay
    (let [user (getenv "ZDL_LEX_MANTIS_USER")
          password (getenv "ZDL_LEX_MANTIS_PASSWORD")]
      (if (and user password)
        (partial merge {:username user :password password})
        (throw (IllegalStateException. "No Mantis API credentials"))))))

(def project
  (delay (getenv "ZDL_LEX_MANTIS_PROJECT" "5")))

(defn- zip->return [loc]
  (zx/xml-> loc dz/children zip/branch? :return))

(defn- zip->items [loc]
  (zx/xml-> loc dz/children zip/branch? :return :item))

(defn- results
  ([op] (results op {}))
  ([op params] (results op params zip->items))
  ([op params zip->locs]
   (log/trace {:op op :params (dissoc params :username :password)})
   (-> (@client op (@authenticate params))
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
  (comp #(or % "-") first #(str/split % #" --")))

(defn issues []
  (let [status (mantis-enum :mc_enum_status)
        severities (mantis-enum :mc_enum_severities)
        resolutions (mantis-enum :mc_enum_resolutions)
        users (mantis-enum :mc_project_get_users {:project_id @project :access 0})]
    (->> (scroll :mc_project_get_issue_headers {:project_id @project})
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

(def mantis-dump
  (delay (fs/data-file "mantis.edn")))

(defn store-dump [data]
  (let [data (->> data
                  (group-by :id) (vals) (map first)
                  (sort-by :last-updated #(compare %2 %1))
                  (vec))]
    (spit @mantis-dump (pr-str data))
    data))

(defn read-dump []
  (try (read-string (slurp @mantis-dump))
       (catch Throwable t (log/debug t) [])))

(defonce index (atom {}))

(defn index-issues [issues]
  (group-by :lemma issues))

(deftimer [mantis issues sync-timer])

(defn- sync-issues []
  (->> (issues) (store-dump)
       (index-issues)
       (reset! index) (count)
       (time! sync-timer)))

(defstate issue-sync-scheduler
  "Synchronizes Mantis issues"
  :start (do
           (future (->> (read-dump) (index-issues) (reset! index)))
           (cron/schedule "0 */15 * * * ?" "Mantis Synchronization" sync-issues))
  :stop (a/close! issue-sync-scheduler))

(defn handle-query [req]
  (htstatus/ok
   (pmap (comp issue :id)
         (get @index (get-in req [:parameters :query :q]) []))))

(defn handle-index-rebuild
  [_]
  (htstatus/ok {:index (a/>!! issue-sync-scheduler :sync)}))

(s/def ::q string?)
(s/def ::issue-query (s/keys :req-un [::q]))

(def query-handler
  {:summary "Query internal index for Mantis issues based on headword"
   :tags ["Mantis" "Query" "Headwords"]
   :parameters {:query ::issue-query}
   :handler handle-query})

(def rebuild-handler
  {:summary "Clears the internal Mantis issue index and re-synchronizes it"
   :tags ["Mantis" "Admin"]
   :handler handle-index-rebuild})

(def ring-handlers
  ["/mantis"
   ["/issues"
    {:get query-handler
     :head query-handler
     :delete rebuild-handler}]])

(comment
  (->> (issues) (take 100) (index-issues))
  (-> (read-dump) (store-dump) last)
  (->> (issues) store-dump index-issues (reset! index) last)
  (->> @index (map vec) (sort-by (comp count second) #(compare %2 %1)) (take 10))
  (->> (read-dump) (index-issues) (reset! index) (count))
  (handle-query {:parameters {:query {:q "schwarz"}}}))
