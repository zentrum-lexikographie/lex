(ns zdl.lex.server.metrics
  (:require [metrics.core :refer [default-registry]]
            [metrics.jvm.core :refer [instrument-jvm]]
            [mount.core :refer [defstate]]
            [zdl.lex.env :refer [getenv]])
  (:import com.codahale.metrics.Slf4jReporter
           java.util.concurrent.TimeUnit))

(def report-interval
  (Integer/parseInt (getenv "METRICS_REPORT_INTERVAL" "0")))

(defstate reporter
  :start
  (when (< 0 report-interval)
    (let [reporter (.build (Slf4jReporter/forRegistry default-registry))]
      #_(instrument-jvm)
      (.start reporter report-interval TimeUnit/MINUTES)
      reporter))
  :stop
  (some-> reporter .close))

