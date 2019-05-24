(ns zdl-lex-client.results
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.article :as article]
            [clojure.string :as str])
  (:import com.jidesoft.swing.JideTabbedPane
           java.awt.event.MouseEvent
           java.awt.Point
           java.awt.Event
           [javax.swing JTabbedPane JTable]
           javax.swing.table.DefaultTableCellRenderer))

(def output (doto (JideTabbedPane. JTabbedPane/BOTTOM)
              (.setShowCloseButtonOnTab true)))

(defn result-tabs [] (ui/select output [:.result]))

(def num-result-tabs (comp count result-tabs))

(defn- result-values [m]
  (->> m vals flatten sort dedupe (str/join ", ")))

(defn result-table [result]
  (let [result (map-indexed #(assoc %2 :num (inc %1)) result)
        result (map #(merge % {:form (some-> % :forms first)
                               :pos (some-> % :pos first)
                               :authors (some-> % :authors result-values)
                               :sources (some-> % :sources result-values)
                               :color (article/status->color %)}) result)
        columns [{:key :num :text "#"}
                 {:key :last-modified :text "Datum"}
                 {:key :form :text "Schreibung"}
                 {:key :pos :text "Wortklasse"}
                 {:key :sources :text "Quellen"}
                 {:key :type :text "Typ"}
                 {:key :authors :text "Autoren"}
                 {:key :status :text "Status"}]
        listeners [:mouse-pressed
                   (fn [^MouseEvent e]
                     (let [^JTable table (.getSource e)
                           ^Point point (.getPoint e)
                           row (.rowAtPoint table point)
                           clicks (.getClickCount e)]
                       (when (and (<= 0 row) (= 2 clicks))
                         (workspace/open-article
                          (nth result row)))))]
        table (ui/table :model [:rows result :columns columns] :listen listeners)
        column-model (.getColumnModel table)]
    (doto table
      (.setAutoResizeMode JTable/AUTO_RESIZE_ALL_COLUMNS))
    (.setCellRenderer
     (.getColumn column-model 7)
     (proxy [DefaultTableCellRenderer] []
       (getTableCellRendererComponent [t v selected? focus? row column]
         (let [component (proxy-super getTableCellRendererComponent
                                      t v selected? focus? row column)
               {:keys [color]} (nth result row)]
           (when-not selected? (ui/config! component :background color))
           component))))
    (.setCellRenderer
     (.getColumn column-model 2)
     (proxy [DefaultTableCellRenderer] []
       (getTableCellRendererComponent [t v selected? focus? row column]
         (let [component (proxy-super getTableCellRendererComponent
                                      t v selected? focus? row column)]
           (ui/config! component :font {:style :bold})
           component))))
    (->> table ui/scrollable)))

(defn add-result [{:keys [id query total result]}]
  (let [id (keyword id)
        title (format "%s (%d)" query total)
        tip query
        tab (result-table result)]
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
               (add-result (merge result {:timestamp (t/now)}))
               (recur)))
           ch)
  :end (async/close! renderer))
