(ns zdl.lex.metrics
  (:require
   [integrant.core :as ig])
  (:import
   (com.codahale.metrics Meter MetricRegistry Slf4jReporter Timer)
   (java.util.concurrent TimeUnit)))


(def ^MetricRegistry metrics-registry
  (MetricRegistry.))

(defmethod ig/init-key ::reporter
  [_ {:keys [interval]}]
  (when (pos? interval)
    (doto (.build (Slf4jReporter/forRegistry metrics-registry))
      (.start interval TimeUnit/MINUTES))))

(defmethod ig/halt-key! ::reporter
  [_ reporter]
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
  [^Timer timer]
  (.time timer))
