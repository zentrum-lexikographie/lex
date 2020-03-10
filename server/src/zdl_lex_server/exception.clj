(ns zdl-lex-server.exception
  (:require [reitit.ring.middleware.exception :as exception]
            [clojure.tools.logging :as log]))

(defn wrap-log-to-console [handler ^Throwable e req]
  (log/warn e (select-keys req [:uri :request-method]))
  (handler e req))

(def middleware
  (exception/create-exception-middleware
   (->> {::exception/wrap wrap-log-to-console}
        (merge exception/default-handlers))))

