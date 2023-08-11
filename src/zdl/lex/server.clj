(ns zdl.lex.server
  (:gen-class)
  (:require
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [zdl.lex.env :as env]
   [zdl.lex.util :refer [exec! install-uncaught-exception-handler!]]))

(install-uncaught-exception-handler!)

(def system
  (atom nil))

(defn start
  []
  (ig/load-namespaces env/config env/server-config-keys)
  (reset! system (ig/init env/config env/server-config-keys))
  (log/info "Started ZDL/Lex Server"))

(defn stop
  []
  (log/info "Stopping ZDL/Lex Server")
  (when-let [system' @system]
    (ig/halt! system')
    (reset! system nil)))

(defn stop-on-shutdown
  []
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable stop)))

(defn start!
  [& _]
  (exec! (fn [& _]
           (stop-on-shutdown)
           (start)
           @(promise))))

(def -main
  start!)
