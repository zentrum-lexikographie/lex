(ns zdl-lex-client.results
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [tick.alpha.api :as t]))

(def output (ui/tabbed-panel :tabs []))

(defn change-results [results]
  (let [tabs-by-id (group-by #(ui/config % :id) (ui/select output [:.result]))
        tab-ids (keys tabs-by-id)

        results-by-id (group-by (comp keyword :id) results)
        result-ids (keys results-by-id)

        new-ids (remove (apply hash-set tab-ids) result-ids)
        removed-ids (remove (apply hash-set result-ids) tab-ids)]

    (ui/invoke-soon
     (doseq [id removed-ids :let [tab (-> id tabs-by-id first)]]
       (.remove output tab))
     (doseq [id (reverse new-ids) :let [result (-> id results-by-id first)
                                        id (name id)]]
       ))))

(def history (atom []))

(defn result-tabs [] (ui/select output [:.result]))

(def num-result-tabs (comp count result-tabs))

(defn add-result [{:keys [id query] :as result}]
  (let [tab (ui/label :id (keyword id) :class :result :text query)]
    (swap! history
           (fn [prev next] (as-> next $ (cons $ prev) (take 10 $) (vec $)))
           result)
    (ui/invoke-soon
     (.insertTab output id nil tab id 0)
     (loop [tabs (num-result-tabs)]
       (when (< 10 tabs)
         (.remove output (dec tabs))
         (recur (num-result-tabs))))
     (.setSelectedIndex output 0))))

(defstate renderer
  :start (let [ch (async/chan)]
           (async/go-loop []
             (when-let [result (async/<! ch)]
               (add-result (merge result {:timestamp (t/now)}))
               (recur)))
           ch)
  :end (async/close! renderer))
