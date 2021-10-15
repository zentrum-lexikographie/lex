(ns zdl.lex.server.graph
  (:require [clojure.tools.logging :as log]
            [manifold.deferred :as d]
            [mount.core :as mount :refer [defstate]]
            [next.jdbc :as jdbc]
            [zdl.lex.server.h2 :as h2]))

(defstate db
  :start (h2/open! "graph")
  :stop  (h2/close! db))

(defn transact!
  [f & {:as opts}]
  (->
   (d/future
     (jdbc/with-transaction [c db opts]
       (f c))
     true)
   (d/catch
       (fn [e]
         (log/warn e "Error while transacting to graph")
         true))))
