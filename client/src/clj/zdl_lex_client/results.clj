(ns zdl-lex-client.results
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [seesaw.swingx :as uix]
            [tick.alpha.api :as t]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as workspace]
            [taoensso.timbre :as timbre])
  (:import com.jidesoft.swing.JideTabbedPane
           [java.awt.event ComponentEvent MouseEvent]
           java.awt.Point
           [javax.swing JTabbedPane JTable]
           javax.swing.table.DefaultTableCellRenderer
           [org.jdesktop.swingx.decorator AbstractHighlighter Highlighter]
           org.jdesktop.swingx.JXTable$TableAdapter))

(def output (doto (JideTabbedPane. JTabbedPane/BOTTOM)
              (.setShowCloseButtonOnTab true)))

(defn result-tabs [] (ui/select output [:.result]))

(def num-result-tabs (comp count result-tabs))

(def ^:private result-table-columns
  [{:key :form :text "Schreibung"}
   {:key :pos :text "Wortklasse"}
   {:key :type :text "Typ"}
   {:key :last-modified :text "Datum"}
   {:key :status :text "Status"}
   {:key :sources :text "Quellen"}
   {:key :authors :text "Autoren"}])

(defn- open-articles-in [result]
  (fn [^MouseEvent e]
    (let [^JTable table (.getSource e)
          ^Point point (.getPoint e)
          row (.rowAtPoint table point)
          clicks (.getClickCount e)]
      (when (and (<= 0 row) (= 2 clicks))
        (workspace/open-article (nth result row))))))

(defn- resize-columns [^ComponentEvent e]
  (let [^JTable table (.getSource e)
        table-width (ui/width table)]
    (doseq [c (range (count result-table-columns))
            :let [column (nth result-table-columns c)
                  model (.. table (getColumnModel) (getColumn c))]]
      (condp = (column :key)
        :form
        (doto model
          (.setMinWidth (int (* 0.25 table-width))))
        model))
    (ui/repaint! table)))

(defn- result-values [m]
  (->> m vals flatten sort dedupe (str/join ", ")))

(def ^:private result->table-model
  (partial map #(merge % {:form (some-> % :forms first)
                          :pos (some-> % :pos first)
                          :authors (some-> % :authors result-values)
                          :sources (some-> % :sources result-values)
                          :color (article/status->color %)})))

(defn result-table [result]
  (let [result (result->table-model result)
        table (uix/table-x :class :result
                           :model [:rows result
                                   :columns result-table-columns]
                           :listen [:mouse-pressed (open-articles-in result)
                                    :component-resized resize-columns]
                           :user-data result)]
    (doto table
      (.setAutoResizeMode
       JTable/AUTO_RESIZE_ALL_COLUMNS)
      (.setHighlighters
       (into-array
        Highlighter
        [(proxy [AbstractHighlighter] []
           (doHighlight [component ^JXTable$TableAdapter adapter]
             (let [column (.column adapter)
                   column (nth result-table-columns column)
                   row (.row adapter)
                   row (nth result row)
                   selected? (.isSelected adapter)]
               (condp = (:key column)
                 :form
                 (ui/config! component :font {:style :bold})
                 :status
                 (if-not selected?
                   (ui/config! component :background (row :color))
                   component)
                 component))))])))
       (->> table ui/scrollable)))

(defn add-result [{:keys [id query total result]}]
  (let [id (keyword id)
        title (format "%s (%d)" query total)
        tip query
        tab (result-table result)]
    (ui/invoke-soon
     (.addTab output title icon/gmd-result tab tip)
     (loop [tabs (num-result-tabs)]
       (when (< 10 tabs)
         (.removeTabAt output 0)
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
