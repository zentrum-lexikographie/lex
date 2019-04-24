(ns zdl-lex-client.bus
  (:require [clojure.core.async :as async]
            [tick.alpha.api :as t])
  (:import java.util.UUID))

(defn uuid [] (str (UUID/randomUUID)))

(defonce article-reqs (async/chan (async/sliding-buffer 3)))

(defonce search-reqs (async/chan (async/sliding-buffer 3)))

(defonce status (atom nil))

(defonce search-results (atom []))

(defn- append-search-result [prev next]
  (as-> next $ (cons $ prev) (take 10 $) (vec $)))

(defn add-search-result [result]
  (let [result (merge result {:timestamp (t/now) :id (uuid)})]
    (swap! search-results append-search-result result)))
