(ns zdl.lex.metrics
  (:require
   [integrant.core :as ig]
   [zdl.lex.env :refer [getenv]])
  (:import
   (com.codahale.metrics Meter MetricRegistry Slf4jReporter Timer Timer$Context)
   (java.util.concurrent TimeUnit)))


(def ^MetricRegistry metrics-registry
  (MetricRegistry.))

(def report-interval
  (parse-long (getenv "METRICS_REPORTER_INTERVAL" "5")))

(defmethod ig/init-key ::reporter
  [_ _]
  (when (pos? report-interval)
    (doto (.build (Slf4jReporter/forRegistry metrics-registry))
      (.start report-interval TimeUnit/MINUTES))))

(defmethod ig/halt-key! ::reporter
  [_ ^Slf4jReporter reporter]
  (when reporter (.close reporter)))

(defn meter
  [k]
  (.meter metrics-registry k))

(defn timer
  [k]
  (.timer metrics-registry k))

(defn metered!
  [^Meter meter]
  (.mark meter))

(defn timed!
  [^Timer timer] ^Timer$Context
  (.time timer))
