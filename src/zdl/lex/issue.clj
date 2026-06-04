(ns zdl.lex.issue
  (:require
   [clojure.string :as str]
   [lambdaisland.uri :as uri]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc.result-set]
   [tick.core :as t]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.index :as index]
   [zdl.lex.metrics :as metrics]))

(def db
  {:dbtype   "mysql"
   :host     (getenv "MANTIS_DB_HOST" "localhost")
   :port     (parse-long (getenv "MANTIS_DB_PORT" "3306"))
   :dbname   (getenv "MANTIS_DB_NAME" "mantis_bugtracker")
   :username (getenv "MANTIS_DB_USER" "mantis")
   :password (getenv "MANTIS_DB_PASSWORD" "mantis")})

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
  (metrics/timer "mantis.sync"))

(defn sync!
  []
  (with-open [_ (metrics/timed! sync-timer)]
   (let [threshold (System/currentTimeMillis)]
     (transduce
      (comp (map parse-issue) (filter :form) (map index/issue->doc)
            (partition-all 10000))
      (completing (fn [n batch] (index/add! batch) (+ n (count batch))))
      0
      (jdbc/plan
       db
       "SELECT
          bug.id as id,
          bug.summary as summary,
          bug.last_updated as updated,
          category.name as category,
          bug.status as status,
          bug.severity as severity,
          reporter.realname as reporter,
          handler.realname as handler,
          bug.resolution as resolution
        FROM mantis_bug_table bug
        LEFT JOIN mantis_user_table reporter ON bug.reporter_id = reporter.id
        LEFT JOIN mantis_user_table handler ON bug.handler_id = handler.id
        LEFT JOIN mantis_category_table category ON bug.category_id = category.id
        WHERE bug.project_id = 5"
       {:builder-fn jdbc.result-set/as-unqualified-kebab-maps}))
     (index/purge! "issue" threshold))))
