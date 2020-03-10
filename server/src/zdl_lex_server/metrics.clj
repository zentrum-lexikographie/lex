(ns zdl-lex-server.metrics
  (:require [mount.core :refer [defstate]]
            [metrics.core :refer [default-registry]]
            [metrics.jvm.core :refer [instrument-jvm]])
  (:import com.codahale.metrics.Slf4jReporter
           java.util.concurrent.TimeUnit))

(defstate reporter
  :start (do
           (instrument-jvm)
           (doto
               (.. (Slf4jReporter/forRegistry default-registry) (build))
             (.start 30 TimeUnit/MINUTES)))
  :stop (.close reporter))

