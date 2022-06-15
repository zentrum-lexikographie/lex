(ns zdl.lex.server.issue
  (:require
   [clojure.core.async :as a]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [honey.sql :as sql]
   [integrant.core :as ig]
   [lambdaisland.uri :as uri]
   [metrics.timers :as timers]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as jdbc.con]
   [next.jdbc.result-set :as jdbc.result-set]
   [zdl.lex.lucene :as lucene]
   [zdl.lex.server.solr.client :as solr.client]
   [zdl.lex.server.solr.fields :as solr.fields])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (java.util Date)))

(defn issue-id->uri
  [id]
  (str (uri/->URI "mantis" nil nil nil nil (str id) nil nil)))

(def status-descs
  {"10" "new",
   "20" "feedback",
   "30" "acknowledged",
   "40" "confirmed",
   "50" "assigned",
   "80" "resolved",
   "90" "closed"})

(def severity-descs
  {"10" "feature",
   "20" "trivial",
   "30" "text",
   "40" "tweak",
   "50" "minor",
   "60" "major",
   "70" "crash",
   "80" "block"})

(def resolution-descs
  {"50" "not fixable",
   "60" "duplicate",
   "30" "reopened",
   "80" "suspended",
   "20" "fixed",
   "90" "won't fix",
   "70" "no change required",
   "10" "open",
   "40" "unable to reproduce"})

(defn execute!
  ([connectable sql]
   (execute! connectable sql {}))
  ([connectable sql opts]
   (jdbc/execute! connectable (sql/format sql) opts)))

(def query-opts
  {:builder-fn jdbc.result-set/as-unqualified-kebab-maps})

(defn query
  ([connectable sql]
   (query connectable sql {}))
  ([connectable sql opts]
   (execute! connectable sql (merge query-opts opts))))

(def issue-query
  {:select    [[:bug.id            :id]
               [:bug.summary       :summary]
               [:bug.last-updated  :last-updated]
               [:category.name     :category]
               [:bug.status        :status]
               [:bug.severity      :severity]
               [:reporter.realname :reporter]
               [:handler.realname  :handler]
               [:bug.resolution    :resolution]]
   :from      [[:mantis-bug-table :bug]]
   :left-join [[:mantis-user-table :reporter]     [:= :bug.reporter-id :reporter.id]
               [:mantis-user-table :handler]      [:= :bug.handler-id :handler.id]
               [:mantis-category-table :category] [:= :bug.category-id :category.id]]
   :where     [:= :bug.project-id 5]})

(defn parse-issue
  [{:keys [id summary status severity resolution last-updated] :as issue}]
  (assoc issue
         :id (issue-id->uri id)
         :last-updated (some-> last-updated java.time.Instant/ofEpochSecond str)
         :form (some-> summary (str/split #" --") first)
         :status (some-> status str status-descs)
         :severity (some-> severity str severity-descs)
         :resolution (some-> resolution str resolution-descs)))

(defn issues
  [db]
  (into [] (map parse-issue) (query db issue-query)))

(def sync-timer
  (timers/timer ["solr" "mantis" "sync-timer"]))

(def sync-xf
  (comp
   (filter :form)
   (map solr.fields/issue->doc)))

(defn sync!
  [db]
  (->>
   (let [threshold (Date.)]
     (log/debugf "Syncing Mantis issue(s)")
     (solr.client/add! (sequence sync-xf (issues db)))
     (solr.client/purge! "issue" threshold))
   (timers/time! sync-timer)))

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

(defmethod ig/init-key ::db
  [_ config]
  (jdbc.con/->pool HikariDataSource config))

(defmethod ig/halt-key! ::db
  [_ ^HikariDataSource ds]
  (.close ds))
