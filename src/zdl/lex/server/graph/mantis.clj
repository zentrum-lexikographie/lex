(ns zdl.lex.server.graph.mantis
  (:require [clj-soap.client :as soap]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.zip :as zip]
            [metrics.timers :as timers]
            [mount.core :refer [defstate]]
            [next.jdbc :as jdbc]
            [zdl.lex.data :as data]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :as fs]
            [zdl.lex.server.graph.db :as graph.db]
            [zdl.lex.server.h2 :as h2]
            [lambdaisland.uri :as uri]
            [zdl.lex.cron :as cron]
            [clojure.core.async :as a]))

(def mantis-base
  (getenv "MANTIS_BASE" "https://mantis.dwds.de/mantis"))

(defn issue-id->uri
  [id]
  (str (uri/->URI "mantis" nil nil nil nil (str id) nil nil)))

(comment
  (issue-id->uri 10))

(defn issue-id->url
  [id]
  (str mantis-base "/view.php?id=" id))

(def client
  (let [wsdl     (str mantis-base "/api/soap/mantisconnect.wsdl")
        endpoint (str mantis-base "/api/soap/mantisconnect.php")]
    (delay
      (soap/client-fn {:wsdl wsdl :options {:endpoint-url endpoint}}))))

(def ^:private user
  (getenv "MANTIS_USER"))

(def ^:private password
  (getenv "MANTIS_PASSWORD"))

(defn authenticate
  [req]
  (when-not (and user password)
    (throw (IllegalStateException. "No Mantis API credentials")))
  (merge {:username user :password password} req))

(def project
  (getenv "MANTIS_PROJECT" "5"))

(defn- zip->return
  [loc]
  (zx/xml-> loc dz/children zip/branch? :return))

(defn- zip->items
  [loc]
  (zx/xml-> loc dz/children zip/branch? :return :item))

(defn- results
  ([op]
   (results op {}))
  ([op params]
   (results op params zip->items))
  ([op params zip->locs]
   (log/trace {:op op :params (dissoc params :username :password)})
   (zip->locs (zip/xml-zip (@client op (authenticate params))))))

(defn scroll
  ([op]
   (scroll op {}))
  ([op params]
   (scroll op params zip->items))
  ([op params zip->locs]
   (scroll op params zip->locs 1000))
  ([op params zip->locs page-size]
   (scroll op params zip->locs page-size 0))
  ([op params zip->locs page-size page]
   (let [params  (merge params {:page_number page :per_page page-size})
         results (seq (results op params zip->locs))]
     (when results
       (concat results
               (lazy-seq (scroll op params zip->locs page-size (inc page))))))))

(defn- property
  [& path]
  (apply zx/xml1-> (concat path [zx/text])))

(defn int-property
  [& path]
  (when-let [v (apply property path)]
    (Integer/parseInt v)))

(defn mantis-enum
  ([op]
   (mantis-enum op {}))
  ([op params]
   (into {}
         (for [item (results op params)]
           [(property item :id) (property item :name)]))))

(defn summary->lemma
  [summary]
  (let [[lemma] (str/split summary #" --")]
    (or lemma "-")))

(defn issues
  []
  (let [status      (mantis-enum :mc_enum_status)
        severities  (mantis-enum :mc_enum_severities)
        resolutions (mantis-enum :mc_enum_resolutions)
        users       (mantis-enum :mc_project_get_users
                                 {:project_id project :access 0})]
    (for [item (scroll :mc_project_get_issue_headers {:project_id project})]
      (let [id      (int-property item :id)
            summary (property item :summary)]
        {:id           id
         :url          (issue-id->url id)
         :category     (property item :category)
         :summary      summary
         :lemma        (some-> summary summary->lemma)
         :last-updated (property item :last_updated)
         :status       (some-> (property item :status) status)
         :severity     (some-> (property item :severity) severities)
         :reporter     (some-> (property item :reporter) users)
         :handler      (some-> (property item :handler) users)
         :resolution   (some-> (property item :resolution) resolutions)
         :attachments  (int-property item :attachments_count)
         :notes        (int-property item :notes_count)}))))

(defn issue
  [id]
  (when-first [item (results :mc_issue_get {:issue_id id} zip->return)]
    (let [id      (int-property item :id)
          summary (property item :summary)]
      {:id           (int-property item :id)
       :url          (issue-id->url id)
       :category     (property item :category)
       :summary      summary
       :lemma        (some-> summary summary->lemma)
       :last-updated (property item :last_updated)
       :status       (some-> (property item :status :name))
       :severity     (some-> (property item :severity :name))
       :reporter     (some-> (property item :reporter :name))
       :handler      (some-> (property item :handler :name))
       :resolution   (some-> (property item :resolution :name))
       :attachments  (some-> (zx/xml-> item :attachments) count)
       :notes        (some-> (zx/xml-> item :notes) count)})))

(def issues->db-batches
  (comp
   (filter :lemma)
   (map (fn [issue]
          [(issue :id)
           (issue :lemma)
           (issue :attachments)
           (issue :last-updated)
           (issue :notes)
           (issue :summary)
           (issue :category)
           (issue :status)
           (issue :severity)
           (issue :reporter)
           (issue :handler)
           (issue :resolution)]))
   (partition-all 10000)))

(def query-timer
  (timers/timer ["graph" "mantis" "query-timer"]))

(defn find-issues-by-forms
  [forms]
  (->>
   (jdbc/with-transaction [c graph.db/pool {:read-only? true}]
     (let [issues (h2/query c {:select :*
                               :from   :mantis_issue
                               :where  [:in :form forms]})
           issues (map #(assoc % :url (issue-id->url (:id %))) issues)]
       (sort-by :last-updated #(compare %2 %1) issues)))
   (timers/time! query-timer)))

(def sync-timer
  (timers/timer ["graph" "mantis" "sync-timer"]))

(defn update-graph!
  []
  (->>
   (jdbc/with-transaction [c graph.db/pool]
     (h2/execute! c {:delete-from :mantis_issue})
     (doseq [batch (sequence issues->db-batches (issues))]
       (h2/execute! c {:insert-into :mantis_issue :values batch})))
   (timers/time! sync-timer)))


(defstate scheduled-update
  :start (cron/schedule "0 */15 * * * ?" "Mantis Synchronization"
                        update-graph!)
  :stop (a/close! scheduled-update))

(comment
  (time (update-graph!))
  (time (find-issues-by-forms ["Test" "spitzfingrig" "schwarz"])))

(defstate remove-mantis-dump
  :start (fs/delete! (data/file "mantis.edn") true))
