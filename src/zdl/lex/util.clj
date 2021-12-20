(ns zdl.lex.util
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import java.util.UUID))

(defn uuid
  []
  (-> (UUID/randomUUID) str str/lower-case))

(defn ->clean-map
  [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))

(defn exec!
  [f & args]
  (try
    (apply f args)
    (finally
      (shutdown-agents))))

(defn install-uncaught-exception-handler!
  []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (log/errorf ex "Uncaught exception on [%s]." (.getName thread))))))
