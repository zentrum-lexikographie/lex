(ns zdl.lex.server
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.data :as data]
            [zdl.lex.fs :refer [path]]
            [zdl.lex.server.http :as http]
            [zdl.lex.server.metrics :as metrics]))

(defn start
  []
  (mount/start)
  (log/infof "Started ZDL/Lex Server @[%s]" (path (data/dir))))

(defn stop
  []
  (log/info "Stopping ZDL/Lex Server")
  (mount/stop))

(defn -main
  [& args]
  (.. (Runtime/getRuntime) (addShutdownHook (Thread. (partial stop))))
  (start)
  (.. (Thread/currentThread) (join))
  (System/exit 0))
