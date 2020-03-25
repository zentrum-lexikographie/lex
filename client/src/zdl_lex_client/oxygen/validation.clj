(ns zdl-lex-client.oxygen.validation
  (:require [mount.core :refer [defstate]]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.workspace :as ws]
            [clojure.tools.logging :as log])
  (:import ro.sync.exml.workspace.api.PluginWorkspace))

(defn add-results
  [{:keys [url errors] :as results}]
  (log/infof "+results: %s" results))

(defn remove-results
  [url]
  (log/infof "-results: %s" url))

(defstate validation-results
  :start
  [(bus/listen :validation add-results)
   (bus/listen :editor-closed (fn [[url]] (remove-results url)))]

  :stop
  (doseq [unsubscribe validation-results] (unsubscribe)))
