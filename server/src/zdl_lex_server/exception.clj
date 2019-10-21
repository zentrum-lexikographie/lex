(ns zdl-lex-server.exception
  (:require [reitit.ring.middleware.exception :as exception]
            [taoensso.timbre :as timbre]))

(defn wrap-log-to-console [handler ^Throwable e req]
  (timbre/warn e (select-keys req [:uri :request-method]))
  (handler e req))

(def middleware
  (exception/create-exception-middleware
   (->> {::exception/wrap wrap-log-to-console}
        (merge exception/default-handlers))))

