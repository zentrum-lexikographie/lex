(ns zdl-lex-client.bus
  (:require [clojure.core.async :as async]))

(defonce article-reqs (async/chan (async/sliding-buffer 3)))

(defonce search-reqs (async/chan (async/sliding-buffer 3)))

(defonce status (atom nil))

(defonce search-results (atom []))

(defn- append-search-result [prev next]
  (as-> next $ (cons $ prev) (take 10 $) (vec $)))

(defn add-search-result [result]
  (swap! search-results append-search-result result))
