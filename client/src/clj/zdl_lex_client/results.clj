(ns zdl-lex-client.results
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [tick.alpha.api :as t]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as workspace])
  (:import com.jidesoft.swing.JideTabbedPane
           [java.awt.event ComponentEvent MouseEvent]
           java.awt.Point
           [javax.swing JTabbedPane JTable]
           [javax.swing.table DefaultTableCellRenderer TableColumn]))

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
        columns [{:key :num :text "#" :class clojure.lang.BigInt
                  :column-config (fn [{:keys [model table-width]}]
                                   (doto ^TableColumn model
                                     (.setMinWidth 50)
                                     (.setMaxWidth (int (* 0.1 table-width)))))}
                 {:key :form :text "Schreibung"
                  :renderer (fn [{:keys [component]}]
                              (ui/config! component :font {:style :bold}))
                  :column-config (fn [{:keys [model table-width]}]
                                   (doto ^TableColumn model
                                     (.setMinWidth (int (* 0.25 table-width)))))}
                 {:key :pos :text "Wortklasse"}
                 {:key :type :text "Typ"}
                 {:key :last-modified :text "Datum"}
                 {:key :status
                  :text "Status"
                  :renderer (fn [{:keys [component value selected? focus?]}]
                              (let [{:keys [color]} value]
                                (when-not selected?
                                  (ui/config! component :background color))))}
                 {:key :sources :text "Quellen"}
                 {:key :authors :text "Autoren"}]
        table (ui/table :class :result :model [:rows result :columns columns])
        ;; merge column models for customization
        columns (map-indexed
                 #(hash-map :column %2
                            :model (.. table (getColumnModel) (getColumn %1)))
                 columns)]
    (doto table
      (.setAutoResizeMode JTable/AUTO_RESIZE_ALL_COLUMNS))
    (doseq [{:keys [column model]} columns
            :let [{:keys [renderer]} column]
            :when renderer]
      (.setCellRenderer
       model
       (proxy [DefaultTableCellRenderer] []
         (getTableCellRendererComponent [t v selected? focus? row column]
           (let [component (proxy-super getTableCellRendererComponent
                                        t v selected? focus? row column)]
             (renderer {:component component
                        :value (nth result row)
                        :selected? selected?
                        :focus? focus?})
             component)))))
    (ui/listen
     table
     :mouse-pressed
     (fn [^MouseEvent e]
       (let [^JTable table (.getSource e)
             ^Point point (.getPoint e)
             row (.rowAtPoint table point)
             clicks (.getClickCount e)]
         (when (and (<= 0 row) (= 2 clicks))
           (workspace/open-article
            (nth result row)))))
     :component-resized
     (fn [^ComponentEvent e]
       (let [^JTable table (.getSource e)
             table-width (ui/width table)]
         (doseq [{:keys [column model]} columns
                 :let [{:keys [column-config]} column]
                 :when column-config]
           (column-config {:column column
                           :model model
                           :table table
                           :table-width table-width}))
         (ui/repaint! table))))
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
