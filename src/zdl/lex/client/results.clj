(ns zdl.lex.client.results
  (:require
   [clojure.string :as str]
   [integrant.core :as ig]
   [seesaw.core :as ui]
   [seesaw.swingx :as uix]
   [seesaw.util :refer [to-dimension]]
   [zdl.lex.article :as article]
   [zdl.lex.bus :as bus]
   [zdl.lex.client.export :as client.export]
   [zdl.lex.client.filter :as client.filter]
   [zdl.lex.client.font :as client.font]
   [zdl.lex.client.http :as client.http]
   [zdl.lex.client.icon :as client.icon])
  (:import
   (com.jidesoft.swing JideTabbedPane)
   (java.awt Point)
   (java.awt.event ComponentEvent MouseEvent)
   (javax.swing Box JTabbedPane JTable)
   (org.jdesktop.swingx JXTable JXTable$TableAdapter)
   (org.jdesktop.swingx.decorator AbstractHighlighter Highlighter)))

(def ^:private result-table-columns
  [{:key :status :text "Status"}
   {:key :source :text "Quelle"}
   {:key :form :text "Schreibung"}
   {:key :definition :text "Definition"}
   {:key :type :text "Typ"}
   {:key :provenance :text "Ersterfassung"}
   {:key :timestamp :text "Datum"}
   {:key :author :text "Autor"}
   {:key :editor :text "Redakteur"}
   {:key :errors :text "Fehler"}])

(defn- open-article
  [result ^MouseEvent e]
  (let [clicks (.getClickCount e)
        ^JXTable table (.getSource e)
        ^Point point (.getPoint e)
        ^JXTable$TableAdapter adapter (.getComponentAdapter table)
        row (.rowAtPoint table point)
        row (if (<= 0 row) (.convertRowIndexToModel adapter row) row)]
    (when (and (= 2 clicks) (<= 0 row))
      (let [{:keys [id]} (nth result row)]
        (bus/publish! #{:open-article} {:id id})))))

(defn- resize-columns
  [^ComponentEvent e]
  (let [^JTable table (.getSource e)
        table-width (ui/width table)]
    (doseq [c (range (count result-table-columns))
            :let [column (nth result-table-columns c)
                  model (.. table (getColumnModel) (getColumn c))]]
      (condp = (column :key)
        :form
        (doto model
          (.setMinWidth (int (* 0.20 table-width))))
        :definition
        (doto model
          (.setMinWidth (int (* 0.20 table-width)))
          (.setMaxWidth (int (* 0.4 table-width))))
        model))
    (ui/repaint! table)))

(defn- result->table-model
  [result]
  (merge result {:form (some-> result :forms first)
                 :definition (some-> result :definitions first)
                 :color (some-> result :status article/status->color)
                 :errors (some->> result :errors sort (str/join ", "))}))

(defn create-highlighter
  [model]
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
          (ui/config! component :font (client.font/derived :style :bold))
          ;; definitions in italic style
          :definition
          (ui/config! component :font (client.font/derived :style :italic))
          ;; status with color
          :status
          (if-not selected?
            (ui/config! component :background (row :color))
            component)
          ;; no-op by default
          component)))))

(def ^:private time-formatter
  (java.time.format.DateTimeFormatter/ofPattern "[HH:mm:ss]"))

(comment
  (.. (java.time.LocalDateTime/now) (format time-formatter)))

(defn- render-result-summary
  [{:keys [query total result] :as data}]
  (let [filter-action (ui/action
                       :icon client.icon/gmd-filter
                       :handler (partial client.filter/open-dialog data))
        query-action (ui/action
                      :icon client.icon/gmd-refresh
                      :handler (fn [_]
                                 (bus/publish! #{:search-request} {:query query})))
        export-action (ui/action
                       :icon client.icon/gmd-export
                       :enabled? (< total 50000)
                       :handler (partial client.export/open-dialog data))]
    (ui/horizontal-panel
     :items [(Box/createRigidArea (to-dimension [5 :by 0]))
             (ui/label :text (.. (java.time.LocalDateTime/now) (format time-formatter))
                       :font (client.font/derived :style :plain))
             (Box/createRigidArea (to-dimension [10 :by 0]))
             (ui/label :text query)
             (Box/createHorizontalGlue)
             (ui/label :text (format "%d Ergebnis(se)" total)
                       :foreground (when (< (count result) total) :orange)
                       :font (client.font/derived :style :plain))
             (Box/createRigidArea (to-dimension [10 :by 0]))
             (ui/toolbar
              :floatable? false
              :items [(ui/button :action filter-action)
                      (ui/button :action export-action)
                      (ui/button :action query-action)])])))

(defn render-result
  [{:keys [result] :as data}]
  (let [model        (map result->table-model result)
        highlighters (into-array Highlighter [(create-highlighter model)])
        table        (uix/table-x
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

(defn get-selected-result
  [pane]
  (when-let [selection (ui/selection pane)]
    (ui/user-data (first (ui/select (get selection :content) [:.result])))))

(def pane
  (let [pane (doto (JideTabbedPane. JTabbedPane/BOTTOM)
               (.setShowCloseButtonOnTab true))]
    (ui/listen pane #{:selection :component-shown}
               (fn [_]
                 (when-let [result (get-selected-result pane)]
                   (bus/publish! #{:search-result-selected} result))))
    pane))

(defn select-result-tabs
  []
  (ui/select pane [:.result]))

(defn count-result-tabs
  []
  (count (select-result-tabs)))

(defn get-result-tab-index
  [tab]
  (.indexOfComponent pane tab))

(defn result=
  [a b]
  (= (:query a) (:query b)))

(defn merge-results
  [query result]
  (ui/invoke-soon
   (let [title        query
         tip          query
         result       (assoc result :query query)
         old-tabs     (filter (comp #(result= result %) ui/user-data)
                          (select-result-tabs))
         insert-index (some->> old-tabs last (get-result-tab-index))
         new-tab      (render-result result)]
     (if insert-index
       (.insertTab pane title client.icon/gmd-result new-tab tip insert-index)
       (.addTab pane title client.icon/gmd-result new-tab tip))
     (doseq [tab old-tabs]
       (.remove pane tab))
     (loop [tabs (count-result-tabs)]
       (when (> tabs 10)
         (.removeTabAt pane 0)
         (recur (count-result-tabs))))
     (ui/selection! pane new-tab)
     (bus/publish! #{:show-view} {:view :results}))))

(defn search
  [_ {:keys [query]}]
  (let [request  {:url          "index"
                  :query-params {:q     query
                                 :limit "1000"}}
        response (client.http/request request)]
    (merge-results query (response :body))))

(defmethod ig/init-key ::events
  [_ _]
  (bus/listen #{:search-request} search))

(defmethod ig/halt-key! ::events
  [_ callback]
  (callback))

(comment
  (search :search-request {:query "*"}))
