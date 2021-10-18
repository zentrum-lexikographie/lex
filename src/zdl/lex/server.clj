(ns zdl.lex.server
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [zdl.lex.data :as data]
            [zdl.lex.fs :refer [path]]
            [zdl.lex.util :refer [exec!]]))

(require 'zdl.lex.server.http)
(require 'zdl.lex.server.metrics)

(defn start
  []
  (mount/start)
  (log/infof "Started ZDL/Lex Server @[%s]" (path (data/dir))))

(defn stop
  []
  (log/info "Stopping ZDL/Lex Server")
  (mount/stop))

(defn stop-on-shutdown
  []
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable stop)))

(defn start!
  [& _]
  (exec! (fn [_]
           (stop-on-shutdown)
           (start)
           (.join (Thread/currentThread)))))

(def -main
  start!)
