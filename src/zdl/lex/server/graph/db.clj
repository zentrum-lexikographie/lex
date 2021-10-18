(ns zdl.lex.server.graph.db
  (:require [mount.core :as mount :refer [defstate]]
            [zdl.lex.server.h2 :as h2]))

(defstate pool
  :start (h2/open! "graph")
  :stop  (h2/close! pool))
