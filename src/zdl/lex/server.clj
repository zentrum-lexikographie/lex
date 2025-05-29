(ns zdl.lex.server
  (:require
   [metrics.core :refer [default-registry]]
   [zdl.lex.env :as env]
   [zdl.lex.server.lock :as lock]
   [zdl.lex.server.git :as git]
   [zdl.lex.server.http :as http]
   [zdl.lex.server.issue :as issue]
   [zdl.lex.server.schedule :as schedule])
  (:import
   (java.util.concurrent TimeUnit)
   (com.codahale.metrics Slf4jReporter)))

(def ^:dynamic metrics-reporter
  nil)

(defn stop-metrics-reporter
  []
  (when metrics-reporter
    (.close metrics-reporter)
    (alter-var-root #'metrics-reporter (constantly nil))))

(defn start-metrics-reporter
  []
  (stop-metrics-reporter)
  (->>
   (doto (.build (Slf4jReporter/forRegistry default-registry))
     (.start env/metrics-report-interval TimeUnit/MINUTES))
   (constantly)
   (alter-var-root #'metrics-reporter)))

(defn start
  []
  (lock/open-db)
  (issue/open-db)
  (git/init!)
  (http/start-server)
  (when env/schedule-tasks? (schedule/start)))

(defn stop
  []
  (when env/schedule-tasks? (schedule/stop))
  (http/stop-server)
  (issue/close-db)
  (lock/close-db)
  (stop-metrics-reporter))

(defn -main
  [& _]
  (. (Runtime/getRuntime) (addShutdownHook (Thread. stop)))
  (start-metrics-reporter)
  (start)
  @(promise))
