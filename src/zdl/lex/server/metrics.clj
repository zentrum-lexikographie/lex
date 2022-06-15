(ns zdl.lex.server.metrics
  (:require
   [integrant.core :as ig]
   [metrics.core :refer [default-registry]])
  (:import
   (com.codahale.metrics Slf4jReporter)
   (java.util.concurrent TimeUnit)))

(defmethod ig/init-key ::reporter
  [_ {:keys [interval]}]
  (doto (.build (Slf4jReporter/forRegistry default-registry))
    (.start interval TimeUnit/MINUTES)))

(defmethod ig/halt-key! ::reporter
  [_ ^Slf4jReporter reporter]
  (.close reporter))
