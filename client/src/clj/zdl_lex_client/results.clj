(ns zdl-lex-client.results
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [seesaw.swingx :as uix]
            [seesaw.util :refer [to-dimension]]
            [tick.alpha.api :as t]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.search :as search])
  (:import com.jidesoft.swing.JideTabbedPane
           [java.awt.event ComponentEvent MouseEvent]
           java.awt.Point
           [javax.swing Box JTabbedPane JTable]
           [org.jdesktop.swingx JXTable JXTable$TableAdapter]
           [org.jdesktop.swingx.decorator AbstractHighlighter Highlighter]))

(def ^:private result-table-columns
  [{:key :status :text "Status"}
   {:key :form :text "Schreibung"}
   {:key :definition :text "Definition"}
   {:key :type :text "Typ"}
   {:key :timestamp :text "Datum"}
   {:key :source :text "Quelle"}
   {:key :author :text "Autor"}])

(defn- open-articles-in [result]
  (fn [^MouseEvent e]
    (let [^JXTable table (.getSource e)
          ^Point point (.getPoint e)
          ^JXTable$TableAdapter adapter (.getComponentAdapter table)
          row (.rowAtPoint table point)
          row (.convertRowIndexToModel adapter row)
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
        :definition
        (doto model
          (.setMinWidth (int (* 0.25 table-width)))
          (.setMaxWidth (int (* 0.5 table-width))))
        model))
    (ui/repaint! table)))

(defn- result-values [m]
  (->> m vals flatten sort dedupe (str/join ", ")))

(def ^:private result->table-model
  (partial map #(merge % {:form (some-> % :forms first)
                          :definition (some-> % :definitions first)
                          :author (some-> % :author)
                          :source (some-> % :source)
                          :color (article/status->color %)})))

(defn render-result [{:keys [result query total] :as data}]
  (let [result (result->table-model result)
        table (uix/table-x :model [:rows result
                                   :columns result-table-columns]
                           :listen [:mouse-pressed (open-articles-in result)
                                    :component-resized resize-columns])
        query-action (ui/action
                      :icon icon/gmd-refresh
                      :handler (fn [_] (search/new-query query)))]
    (doto table
      (.setAutoResizeMode
       JTable/AUTO_RESIZE_ALL_COLUMNS)
      (.setHighlighters
       (into-array
        Highlighter
        [(proxy [AbstractHighlighter] []
           (doHighlight [component ^JXTable$TableAdapter adapter]
             (let [column (.column adapter)
                   column (.convertColumnIndexToModel adapter column)
                   column (nth result-table-columns column)
                   row (.row adapter)
                   row (.convertRowIndexToModel adapter row)
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
    (ui/border-panel
     :class :result :user-data data
     :north (ui/horizontal-panel
             :items [(Box/createRigidArea (to-dimension [5 :by 0]))
                     (ui/label :text (t/format "[HH:mm:ss]" (t/date-time))
                               :font {:style :plain})
                     (Box/createRigidArea (to-dimension [10 :by 0]))
                     (ui/label :text query)
                     (Box/createHorizontalGlue)
                     (ui/label :text (format "%d Ergebnis(se)" total)
                               :foreground (if (< (count result) total) :orange)
                               :font {:style :plain})
                     (Box/createRigidArea (to-dimension [10 :by 0]))
                     (ui/toolbar
                      :floatable? false
                      :items [(ui/button :action query-action)])])
     :center (ui/scrollable table))))

(def output (doto (JideTabbedPane. JTabbedPane/BOTTOM)
              (.setShowCloseButtonOnTab true)))

(defn result-tabs [] (ui/select output [:.result]))

(def num-result-tabs (comp count result-tabs))

(defn result= [a b]
  (= (:query a) (:query b)))

(defn merge-result [{:keys [id query total result] :as data}]
  (ui/invoke-soon
   (let [id (keyword id)
         title query
         tip query
         timestamp (t/now)
         old-tabs (filter #(result= data (-> % ui/user-data)) (result-tabs))
         insert-index (some->> old-tabs last (.indexOfComponent output))
         new-tab (render-result data)]
     (if insert-index
       (.insertTab output title icon/gmd-result new-tab tip insert-index)
       (.addTab output title icon/gmd-result new-tab tip))
     (doseq [tab old-tabs] (.remove output tab))
     (loop [tabs (num-result-tabs)]
       (when (> tabs 10)
         (.removeTabAt output 0)
         (recur (num-result-tabs))))
     (ui/selection! output new-tab)
     (workspace/show-view workspace/results-view))))

(defstate renderer
  :start (let [ch (async/chan)]
           (async/go-loop []
             (when-let [result (async/alt! [ch search/responses] ([v] v))]
               (merge-result result)
               (recur)))
           ch)
  :stop (async/close! renderer))
