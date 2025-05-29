(ns zdl.lex.server.issue
  (:require
   [clojure.string :as str]
   [honey.sql :as sql]
   [lambdaisland.uri :as uri]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as jdbc.con]
   [next.jdbc.result-set :as jdbc.result-set]
   [taoensso.telemere :as tm]
   [zdl.lex.env :as env]
   [zdl.lex.server.index :as index]
   [tick.core :as t])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

(def ^:dynamic db
  nil)

(defn close-db
  []
  (when db (.close db) (alter-var-root #'db (constantly nil))))

(defn open-db
  []
  (close-db)
  (tm/log! :info (format "Opened %s:%s/%s"
                         (:dbtype env/mantis-db)
                         (:host env/mantis-db)
                         (:dbname env/mantis-db)))
  (->> (jdbc.con/->pool HikariDataSource env/mantis-db)
       (constantly)
       (alter-var-root #'db)))


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


(def issue-query
  (sql/format
   {:select    [[:bug.id            :id]
                [:bug.summary       :summary]
                [:bug.last-updated  :updated]
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
    :where     [:= :bug.project-id 5]}))

(def issue-query-opts
  {:builder-fn jdbc.result-set/as-unqualified-kebab-maps})

(defn parse-issue
  [{:keys [id summary status severity resolution updated] :as issue}]
  (assoc issue
         :id (issue-id->uri id)
         :updated (some-> updated (* 1000) (t/instant) str)
         :form (some-> summary (str/split #" --") first)
         :status (some-> status str status-descs)
         :severity (some-> severity str severity-descs)
         :resolution (some-> resolution str resolution-descs)))

(def sync-timer
  (env/timer "mantis.sync"))

(defn sync!
  []
  (with-open [_ (env/timed! sync-timer)]
   (let [threshold (System/currentTimeMillis)]
     (transduce
      (comp (map parse-issue) (filter :form) (map index/issue->doc)
            (partition-all 10000))
      (completing (fn [n batch] (index/add! batch) (+ n (count batch))))
      0
      (jdbc/plan db issue-query issue-query-opts))
     (index/purge! "issue" threshold))))
