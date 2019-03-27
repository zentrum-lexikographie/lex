(ns zdl-lex-server.api
  (:require [mount.core :refer [defstate]]
            [zdl-lex-server.store :as store]
            [ring.util.http-response :as htstatus]))

(defstate handler
  :start
  (fn [req]
    (htstatus/ok (->> store/article-files (map #(.getName %)) sort))))
