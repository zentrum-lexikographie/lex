(ns zdl.lex.server.issue
  (:require [clj-soap.client :as soap]
            [clojure.core.async :as a]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as zx]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.zip :as zip]
            [lambdaisland.uri :as uri]
            [metrics.timers :as timers]
            [mount.core :refer [defstate]]
            [zdl.lex.cron :as cron]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.lucene :as lucene]
            [zdl.lex.server.solr.client :as solr.client]
            [zdl.lex.server.solr.fields :as solr.fields])
  (:import java.util.Date))

(def mantis-base
  (uri/uri (getenv "MANTIS_BASE" "https://mantis.dwds.de/mantis/")))

(defn issue-id->uri
  [id]
  (str (uri/->URI "mantis" nil nil nil nil (str id) nil nil)))

(def client
  (let [wsdl     (str (uri/join mantis-base
                                "api/soap/mantisconnect.wsdl") )
        endpoint (str (uri/join mantis-base
                                "api/soap/mantisconnect.php"))]
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

(defn summary->form
  [summary]
  (let [[form] (str/split summary #" --")]
    (or form "-")))

(defn issues
  []
  (let [status      (mantis-enum :mc_enum_status)
        severities  (mantis-enum :mc_enum_severities)
        resolutions (mantis-enum :mc_enum_resolutions)
        users       (mantis-enum :mc_project_get_users
                                 {:project_id project :access 0})]
    (for [item  (scroll :mc_project_get_issue_headers {:project_id project})
          :let  [id      (issue-id->uri (int-property item :id))
                 summary (property item :summary)
                 form    (some-> summary (str/split #" --") first)]
          :when form]
      {:id           id
       :summary      summary
       :form         form
       :last-updated (property item :last_updated)
       :category     (property item :category)
       :status       (some-> (property item :status) status)
       :severity     (some-> (property item :severity) severities)
       :reporter     (some-> (property item :reporter) users)
       :handler      (some-> (property item :handler) users)
       :resolution   (some-> (property item :resolution) resolutions)
       :attachments  (int-property item :attachments_count)
       :notes        (int-property item :notes_count)})))

(def sync-timer
  (timers/timer ["solr" "mantis" "sync-timer"]))

(defn sync!
  []
  (->>
   (let [issues    (issues)
         threshold (Date.)]
     (log/debugf "Syncing %d Mantis issue(s)" (count issues))
     (doseq [issue-batch (partition-all 10000 issues)]
       (solr.client/add! (map solr.fields/issue->doc issue-batch)))
     (log/debugf "Purging Mantis issues before %s" threshold)
     (solr.client/purge! "issue" threshold))
   (timers/time! sync-timer)))

(defstate scheduled-sync
  :start (cron/schedule "0 */15 * * * ?" "Mantis Sync" sync!)
  :stop (a/close! scheduled-sync))

(defn value->clause
  [v]
  [:clause [:value [:quoted v]]])

(defn request->lucene-query
  [{:keys [q]}]
  (let [vs (some->> (if (string? q) [q] q) (into (sorted-set)))]
    (when vs
      [:query
       [:clause
        [:field [:term "form_s"]]
        [:sub-query (into [:query] (interpose [:or] (map value->clause vs)))]]])))

(defn request->query
  [{{:keys [query]} :parameters}]
  (when-let [q (request->lucene-query query)]
    {"q"    (lucene/ast->str q)
     "fq"   "doc_type:issue"
     "fl"   "abstract_ss"
     "rows" "1000"}))

(defn parse-response
  [{{{:keys [numFound docs]} :response} :body}]
  {:total  numFound
   :result (map solr.fields/doc->abstract docs)})

(def query-timer
  (timers/timer ["solr" "mantis" "query-timer"]))

(defn handle-query
  [req]
  (a/go
    (if-let [query (request->query req)]
      (if-let [response (a/<! (solr.client/query query))]
        (do
          (solr.client/update-timer! query-timer response)
          {:status 200
           :body   (parse-response response)})
        {:status 502})
      {:status 404})))

(comment
  (take 10 (map solr.fields/issue->doc (issues)))
  (time (sync!)))
