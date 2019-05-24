(ns zdl-lex-client.results
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as workspace])
  (:import com.jidesoft.swing.JideTabbedPane
           java.awt.event.MouseEvent
           java.awt.Point
           javax.swing.JTable
           javax.swing.JTabbedPane))

(def output (doto (JideTabbedPane. JTabbedPane/BOTTOM)
              (.setShowCloseButtonOnTab true)))

(defn result-tabs [] (ui/select output [:.result]))

(def num-result-tabs (comp count result-tabs))

(defn add-result [{:keys [id query total result] :as result}]
  (let [id (keyword id)
        title (format "%s (%d)" query total)
        tip query
        tab (->> (ui/table
                  :model [:rows result
                          :columns [{:key :forms :text "Schreibung"}
                                    {:key :pos :text "Wortklasse"}
                                    {:key :sources :text "Quellen"}
                                    {:key :type :text "Typ"}
                                    {:key :last-modified :text "Datum"}
                                    {:key :status :text "Status"}]]
                  :listen [:mouse-pressed
                           (fn [^MouseEvent e]
                             (let [^JTable table (.getSource e)
                                   ^Point point (.getPoint e)
                                   row (.rowAtPoint table point)
                                   clicks (.getClickCount e)]
                               (when (and (<= 0 row) (= 2 clicks))
                                 (workspace/open-article
                                  (nth result row)))))])
                 (ui/scrollable)
                 (ui/border-panel :center))]
    (ui/invoke-soon
     (.insertTab output title icon/gmd-result tab tip 0)
     (loop [tabs (num-result-tabs)]
       (when (< 10 tabs)
         (.remove output (dec tabs))
         (recur (num-result-tabs))))
     (ui/selection! output tab)
     (workspace/show-view workspace/results-view))))

(defstate renderer
  :start (let [ch (async/chan)]
           (async/go-loop []
             (when-let [result (async/<! ch)]
               (add-result (merge (timbre/spy :info result) {:timestamp (t/now)}))
               (recur)))
           ch)
  :end (async/close! renderer))
