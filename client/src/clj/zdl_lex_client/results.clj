(ns zdl-lex-client.results
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [tick.alpha.api :as t]
            [zdl-lex-client.icon :as icon]
            [taoensso.timbre :as timbre]))

(def output (ui/tabbed-panel :tabs [] :overflow :scroll :placement :bottom))

(defn result-tabs [] (ui/select output [:.result]))

(def num-result-tabs (comp count result-tabs))

(defn add-result [{:keys [id query total] :as result}]
  (let [id (keyword id)
        title (format "%s (%d)" query total)
        tip query
        result (->> (ui/table
                     :model [:columns [{:key :forms :text "Schreibung"}
                                       {:key :status :text "Status"}
                                       {:key :last-modified :text "Änderung"}
                                       {:key :type :text "Typ"}
                                       {:key :id :text "…"}]
                             :rows (:result result)])
                    (ui/scrollable)
                    (ui/border-panel :center))]
    (ui/invoke-soon
     (.insertTab output title icon/gmd-result result tip 0)
     (loop [tabs (num-result-tabs)]
       (when (< 10 tabs)
         (.remove output (dec tabs))
         (recur (num-result-tabs))))
     (ui/selection! output result))))

(defstate renderer
  :start (let [ch (async/chan)]
           (async/go-loop []
             (when-let [result (async/<! ch)]
               (add-result (merge (timbre/spy :info result) {:timestamp (t/now)}))
               (recur)))
           ch)
  :end (async/close! renderer))
