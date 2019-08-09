(ns zdl-lex-client.view.results
  (:require [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [seesaw.swingx :as uix]
            [seesaw.util :refer [to-dimension]]
            [tick.alpha.api :as t]
            [zdl-lex-common.article :as article]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.workspace :as ws])
  (:import com.jidesoft.swing.JideTabbedPane
           [java.awt.event ComponentEvent MouseEvent]
           java.awt.Point
           [javax.swing Box JTabbedPane JTable]
           [org.jdesktop.swingx JXTable JXTable$TableAdapter]
           [org.jdesktop.swingx.decorator AbstractHighlighter Highlighter]))

(def ^:private result-table-columns
  [{:key :status :text "Status"}
   {:key :source :text "Quelle"}
   {:key :form :text "Schreibung"}
   {:key :definition :text "Definition"}
   {:key :type :text "Typ"}
   {:key :timestamp :text "Datum"}
   {:key :author :text "Autor"}])

(defn- open-article [result ^MouseEvent e]
  (let [clicks (.getClickCount e)
        ^JXTable table (.getSource e)
        ^Point point (.getPoint e)
        ^JXTable$TableAdapter adapter (.getComponentAdapter table)
        row (.rowAtPoint table point)
        row (if (<= 0 row) (.convertRowIndexToModel adapter row) row)]
    (when (and (= 2 clicks) (<= 0 row))
      (let [{:keys [id]} (nth result row)]
        (ws/open-article ws/instance id)))))

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

(defn- result->table-model [result]
  (merge result {:form (some-> result :forms first)
                 :definition (some-> result :definitions first)
                 :author (some-> result :author)
                 :source (some-> result :source)
                 :color (some-> result :status article/status->color)}))

(defn create-highlighter [model]
  (proxy [AbstractHighlighter] []
    (doHighlight [component ^JXTable$TableAdapter adapter]
      (let [column (.column adapter)
            column (.convertColumnIndexToModel adapter column)
            column (nth result-table-columns column)
            row (.row adapter)
            row (.convertRowIndexToModel adapter row)
            row (nth model row)
            selected? (.isSelected adapter)]
        (condp = (:key column)
          ;; forms in bold style
          :form
          (ui/config! component :font {:style :bold})
          ;; definitions in italic style
          :definition
          (ui/config! component :font {:style :italic})
          ;; status with color
          :status
          (if-not selected?
            (ui/config! component :background (row :color))
            component)
          ;; no-op by default
          component)))))

(defn- render-result-summary [{:keys [query total result] :as data}]
  (let [query-action (ui/action
                      :icon icon/gmd-refresh
                      :handler (fn [_] (search/request query)))]
    (ui/horizontal-panel
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
              :items [(ui/button :action query-action)])])))

(defn render-result [{:keys [query total] :as data}]
  (let [model (map result->table-model (data :result))
        highlighters (into-array Highlighter [(create-highlighter model)])
        table (uix/table-x
               :model [:rows model :columns result-table-columns]
               :listen [:mouse-pressed (partial open-article model)
                        :component-resized resize-columns])]
    (ui/border-panel
     :class :result
     :user-data data
     :north (render-result-summary data)
     :center (ui/scrollable
              (doto table
                (.setAutoResizeMode JTable/AUTO_RESIZE_ALL_COLUMNS)
                (.setHighlighters highlighters))))))

(defn get-selected-result [pane]
  (some-> pane ui/selection :content
          (ui/select [:.result]) first
          ui/user-data))

(def tabbed-pane
  (let [pane (JideTabbedPane. JTabbedPane/BOTTOM)]
    (doto pane
      (.setShowCloseButtonOnTab true)
      (ui/listen :selection
                 (fn [_] (->> (or (get-selected-result pane) {})
                              (bus/publish! :search-result)))))))

(defn select-result-tabs []
  (ui/select tabbed-pane [:.result]))

(defn count-result-tabs []
  (count (select-result-tabs)))

(defn get-result-tab-index [tab]
  (.indexOfComponent tabbed-pane tab))

(defn result= [a b]
  (= (:query a) (:query b)))

(defn merge-results [resp]
  (ui/invoke-soon
   (let [id (-> resp :id keyword)
         title (resp :query)
         tip (resp :query)
         timestamp (t/now)
         old-tabs (filter (comp (partial result= resp) ui/user-data)
                          (select-result-tabs))
         insert-index (some->> old-tabs last (get-result-tab-index))
         new-tab (render-result resp)]
     (if insert-index
       (.insertTab tabbed-pane title icon/gmd-result new-tab tip insert-index)
       (.addTab tabbed-pane title icon/gmd-result new-tab tip))
     (doseq [tab old-tabs] (.remove tabbed-pane tab))
     (loop [tabs (count-result-tabs)]
       (when (> tabs 10)
         (.removeTabAt tabbed-pane 0)
         (recur (count-result-tabs))))
     (ui/selection! tabbed-pane new-tab)
     (ws/show-view ws/instance :results))))

(defstate renderer
  :start (let [subscription (bus/subscribe :search-response)]
           (s/consume merge-results subscription)
           subscription)
  :stop (s/close! renderer))
