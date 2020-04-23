(ns zdl.lex.server.metrics
  (:require [mount.core :refer [defstate]]
            [metrics.core :refer [default-registry]]
            [metrics.jvm.core :refer [instrument-jvm]]
            [zdl.lex.env :refer [env]])
  (:import com.codahale.metrics.Slf4jReporter
           java.util.concurrent.TimeUnit))

(defstate reporter
  :start (let [interval (env :metrics-report-interval)
               reporter (.build (Slf4jReporter/forRegistry default-registry))]
           (instrument-jvm)
           (.start reporter interval TimeUnit/MINUTES)
           reporter)
  :stop (.close reporter))

