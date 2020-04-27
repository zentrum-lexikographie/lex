(ns dev
  (:require [zdl.lex.server :as server]))

(set! *warn-on-reflection* true)

(defn start
  []
  (server/start))

(defn stop
  []
  (server/stop))
