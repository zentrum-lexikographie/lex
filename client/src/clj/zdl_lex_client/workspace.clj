(ns zdl-lex-client.workspace
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [zdl-lex-client.url :as url])
  (:import java.net.URL))

(defonce instance (atom nil))

(defstate article-opener
  :start (let [ch (async/chan (async/sliding-buffer 3))]
           (async/go-loop []
             (when-let [article-req (async/<! ch)]
               (some-> @instance (.open (-> article-req :id url/article str (URL.))))
               (recur)))
           ch)
  :stop (async/close! article-opener))
