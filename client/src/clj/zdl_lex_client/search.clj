(ns zdl-lex-client.search
  (:require [seesaw.bind :as uib]
            [mount.core :refer [defstate]]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.query :as query])
  (:import java.util.UUID))

(def query (atom ""))

(def query-valid? (atom true))

(defstate result->query
  :start (uib/bind (bus/bind :search-result)
                   (uib/transform :query)
                   (uib/filter identity)
                   query)
  :stop (result->query))

(defstate query->valid?
  :start (uib/bind query
                   (uib/transform query/valid?)
                   query-valid?)
  :stop (query->valid?))

(defn request [q]
  (reset! query q)
  (bus/publish! :search-request {:query q :id (str (UUID/randomUUID))}))
