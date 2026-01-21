(ns zdl.lex.server
  (:require
   [zdl.lex.env :as env]
   [zdl.lex.server.db :as db]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.gpt :as gpt]
   [zdl.lex.server.http :as http]
   [zdl.lex.server.issue :as issue]
   [zdl.lex.server.schedule :as schedule]))

(defn start
  []
  (db/open!)
  (issue/open-db)
  (gpt/connect)
  (git/init!)
  (http/start-server)
  (when env/schedule-tasks? (schedule/start)))

(defn stop
  []
  (when env/schedule-tasks? (schedule/stop))
  (http/stop-server)
  (gpt/disconnect)
  (issue/close-db)
  (db/close!)
  (env/stop-metrics-reporter))

(defn -main
  [& _]
  (. (Runtime/getRuntime) (addShutdownHook (Thread. stop)))
  (env/start-metrics-reporter)
  (start)
  @(promise))
