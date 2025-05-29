(ns zdl.lex.ui.search
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [gremid.xml :as gx]
   [seesaw.behave :as behave]
   [seesaw.bind :as uib]
   [seesaw.border :refer [line-border]]
   [seesaw.chooser :as chooser]
   [seesaw.core :as ui]
   [seesaw.forms :as forms]
   [seesaw.swingx :as uix]
   [seesaw.util :refer [to-dimension]]
   [taoensso.telemere :as tm]
   [zdl.lex.article :as article]
   [zdl.lex.lucene :as lucene]
   [zdl.lex.oxygen.workspace :as workspace]
   [zdl.lex.ui.util :as util]
   [zdl.lex.client :as client]
   [tick.core :as t])
  (:import
   (com.jidesoft.hints AbstractListIntelliHints)
   (com.jidesoft.swing JideTabbedPane)
   (java.awt Point)
   (java.awt.event ComponentEvent MouseEvent)
   (java.io File)
   (javax.swing Box JTabbedPane JTable)
   (org.jdesktop.swingx JXTable JXTable$TableAdapter)
   (org.jdesktop.swingx.decorator AbstractHighlighter Highlighter)))

;; # Search

(def input
  (ui/text :columns 40 :font (util/derived-font :style :plain)))

(behave/when-focused-select-all input)

(uib/bind
 input
 (uib/transform #(if (lucene/valid? %) :black :red))
 (uib/property input :foreground))

(defn suggest?
  [q]
  (and (< 1 (count q)) (re-matches #"^[^\*\?\"/:]+$" q)))

(defn- suggestion->html
  [{:keys [snippet pos type definitions status id last-modified]}]
  (let [pos               (-> pos first (or "?"))
        source            (re-find #"^[^/]+" id)
        source            (str "<font color=\"#ccc\">[" source "]</font>")
        title             (str/join " " (remove nil? [snippet pos source]))
        subtitle          (str/join ", " (remove nil? [type status last-modified]))
        subtitle          (str "<font color=\"#999\">" subtitle "</font>")
        definition        (-> definitions first (or "[ohne Def.]"))
        definition-length (count definition)
        definition        (subs definition 0 (min 40 definition-length))
        definition        (str definition (if (< 40 definition-length) "…" ""))
        definition        (str "<font color=\"#999\"><i>" definition "</i></font>")
        html              (str/join "<br>" (remove nil? [title subtitle definition]))]
    (str "<html>" html "</html>")))

(defn- suggestion->border
  [{:keys [status]}]
  [5 (line-border :color (article/status->color status) :right 10) 5])

(defn- render-suggestion-list-entry
  [this {:keys [value]}]
  (ui/config! this
              :font (util/derived-font :style :plain)
              :text (suggestion->html value)
              :border (suggestion->border value)))

(defn suggest
  [q]
  (tm/with-ctx+ {::suggest-query q}
    (try (client/http-suggest q) (catch Throwable t (tm/error! t) []))))

(proxy [AbstractListIntelliHints] [input]
  (createList []
    (ui/config! (proxy-super createList)
                :background :white
                :renderer render-suggestion-list-entry))
  (updateHints [ctx]
    (let [q           (str ctx)
          suggestions (if (suggest? q) (suggest q) [])]
      (proxy-super setListData (into-array Object suggestions))
      (some? (seq suggestions))))
  (acceptHint [{:keys [id]}]
    (ui/config! input :text "")
    (workspace/open-article id)))

(def action
  (ui/action :name "Suchen"
             :icon (util/icon :search)
             :handler (fn [_] (let [q (ui/value input)]
                                (when (lucene/valid? q)
                                  (client/query q))))))

(ui/config! input :action action)

;; # Export to CSV

(defn csv-ext?
  [^File f]
  (.. f (getName) (toLowerCase) (endsWith ".csv")))

(defn choose-csv-file ^File
  [parent]
  (when-let [^File f (chooser/choose-file parent
                                          :type :save
                                          :filters [["CSV" ["csv"]]]
                                          :all-files? false)]
    (if (csv-ext? f) f (io/file (str (.. f (getPath)) ".csv")))))

(defn create-export-progress-dialog
  [parent ^File f {:keys [total]}]
  (ui/dialog :title "Ergebnisse exportieren"
             :parent parent
             :content (forms/forms-panel
                       "center:150dlu"
                       :items
                       [(ui/label :text (format "%d Ergebnis(se)" total))
                        (ui/progress-bar :indeterminate? true)
                        (ui/label :text (str f))])
             :options []))

(defn open-export-dialog
  ([results]
   (open-export-dialog results nil))
  ([{:keys [query] :as results} parent]
   (let [parent (some-> parent ui/to-root)]
     (when-let [csv-file (choose-csv-file parent)]
       (let [progress-dialog (create-export-progress-dialog parent csv-file results)]
         (future
           (try
             (client/http-export-to-file query csv-file)
             (catch Throwable t
               (ui/alert (.getMessage t)))
             (finally
               (ui/dispose! progress-dialog))))
         (-> progress-dialog (ui/pack!) (ui/show!) (ui/invoke-soon)))))))


;; # Results Filter

(def facet-title
  {:status     "Status"
   :author     "Autor"
   :editor     "Redakteur"
   :errors     "Fehler"
   :source     "Quelle"
   :type       "Typ"
   :tranche    "Tranche"
   :provenance "Ersterfassung"
   :timestamp  "Zeitstempel"})

(def facet-field
  {:status     "status"
   :author     "autor"
   :editor     "red"
   :errors     "fehler"
   :source     "quelle"
   :type       "typ"
   :tranche    "tranche"
   :provenance "ersterfassung"
   :timestamp  "datum"})

(defn render-ts-facet-list-entry
  [this {:keys [value]}]
  (let [[v n] value
        v     (t/format "'ab 'dd.MM.yy" (t/date v))]
    (ui/config! this
                :text (str v " (" n ")")
                :font (util/derived-font :style :plain)
                :border 5)))

(defn render-facet-list-entry
  [this {:keys [value]}]
  (let [[v n] value]
    (ui/config! this
                :text (str v " (" n ")")
                :font (util/derived-font :style :plain)
                :border 5)))

(defn facet-lists->query
  [facet-lists]
  (->>
   (for [fl    facet-lists
         :let  [k (ui/user-data fl)
                vs (map first (ui/selection fl {:multi? true}))]
         :when (seq vs)]
     [:c
      [:field (facet-field k)]
      (condp = k
        :timestamp
        [:range {:start "inclusive" :end "inclusive"}
         (-> vs first t/date str) [:to] "*"]
        (let [vs (map (fn [v] [:v (lucene/escape-term v) ]) vs)]
          (if (= 1 (count vs))
            (first vs)
            [:sub (->> (interpose [:or] vs) (cons :q) (vec))])))])
   (interpose [:and])
   (cons :q)
   (vec)
   (gx/sexp->node)
   (lucene/node->str)))

(defn filter!
  [query facet-lists e]
  (some->>
   (remove empty? [(facet-lists->query facet-lists) query])
   (str/join " AND ")
   (not-empty)
   (client/query))
  (ui/dispose! e))

(defn open-filter-dialog
  [{:keys [query facets]} & args]
  (let [result->list-model #(->> (or (some-> facets %) {})
                                 (into [])
                                 (sort-by first))
        facet-lists        (for [k [:status :author :editor :timestamp
                                    :source :tranche :type :provenance :errors]]
                             (condp = k
                               :timestamp
                               (ui/listbox :model (result->list-model k)
                                           :selection-mode :single
                                           :renderer render-ts-facet-list-entry
                                           :class :facet-list
                                           :user-data k)
                               (ui/listbox :model (result->list-model k)
                                           :selection-mode :multi-interval
                                           :renderer render-facet-list-entry
                                           :class :facet-list
                                           :user-data k)))
        lists              (->>
                            (map #(ui/scrollable % :border [5 (-> % ui/user-data facet-title)])
                                 facet-lists))
        help               (->> (str "<html>"
                                     "<b>Tipp:</b> "
                                     "Auswahl mehrerer Einträge einer Liste mit &lt;Strg&gt;."
                                     "</html>")
                                (ui/label :border 5 :text))
        content            (ui/border-panel
                            :center (ui/grid-panel
                                     :rows 2
                                     :columns 5
                                     :items lists
                                     :hgap 5
                                     :vgap 5)
                            :south help
                            :hgap 5
                            :vgap 5)
        options            [(ui/button :text "Filtern"
                            :listen [:action (partial filter! query facet-lists)])
                            (ui/button :text "Abbrechen"
                            :listen [:action ui/dispose!])]]
    (-> (ui/dialog :title "Suchfilter"
                   :type :question
                   :parent (some-> args first ui/to-root)
                   :size (util/clip-to-screen-size [800 :by 600])
                   :content content
                   :options options)
        ui/pack!
        ui/show!
        ui/invoke-later)))

;; # Results

(def result-table-columns
  [{:key :status :text "Status"}
   {:key :form :text "Schreibung"}
   {:key :type :text "Typ"}
   {:key :author :text "Autor"}
   {:key :editor :text "Redakteur"}
   {:key :timestamp :text "Datum"}
   {:key :source :text "Quelle"}
   {:key :definition :text "Definition"}
   {:key :provenance :text "Ersterfassung"}
   {:key :errors :text "Fehler"}])

(defn- resize-columns
  [^ComponentEvent e]
  (let [^JTable table (.getSource e)
        table-width   (ui/width table)]
    (doseq [c (range (count result-table-columns))]
      (let [column (nth result-table-columns c)
            model  (.. table (getColumnModel) (getColumn c))]
        (condp = (column :key)
          :form
          (doto model
            (.setMinWidth (int (* 0.20 table-width))))
          :definition
          (doto model
            (.setMinWidth (int (* 0.20 table-width)))
            (.setMaxWidth (int (* 0.4 table-width))))
          model)))
    (ui/repaint! table)))

(defn- result->table-model
  [result]
  (merge result {:form       (some-> result :forms first)
                 :definition (some-> result :definitions first)
                 :color      (some-> result :status article/status->color)
                 :errors     (some->> result :errors sort (str/join ", "))}))

(defn create-highlighter
  [model]
  (proxy [AbstractHighlighter] []
    (doHighlight [component ^JXTable$TableAdapter adapter]
      (let [column    (.column adapter)
            column    (.convertColumnIndexToModel adapter column)
            column    (nth result-table-columns column)
            row       (.row adapter)
            row       (.convertRowIndexToModel adapter row)
            row       (nth model row)
            selected? (.isSelected adapter)]
        (condp = (:key column)
          ;; forms in bold style
          :form
          (ui/config! component :font (util/derived-font :style :bold))
          ;; definitions in italic style
          :definition
          (ui/config! component :font (util/derived-font :style :italic))
          ;; status with color
          :status
          (if-not selected?
            (ui/config! component :background (row :color))
            component)
          ;; no-op by default
          component)))))

(defn- render-result-summary
  [{:keys [query total result timestamp] :as data}]
  (let [filter-action (ui/action
                       :icon (util/icon :filter-list)
                       :handler (partial open-filter-dialog data))
        query-action  (ui/action
                      :icon (util/icon :refresh)
                      :handler (fn [_] (client/query query)))
        export-action (ui/action
                       :icon (util/icon :save)
                       :enabled? (< total 50000)
                       :handler (partial open-export-dialog data))]
    (ui/horizontal-panel
     :items [(Box/createRigidArea (to-dimension [5 :by 0]))
             (ui/label :text (t/format "[HH:mm:ss]" timestamp)
                       :font (util/derived-font :style :plain))
             (Box/createRigidArea (to-dimension [10 :by 0]))
             (ui/label :text query)
             (Box/createHorizontalGlue)
             (ui/label :text (format "%d Ergebnis(se)" total)
                       :foreground (when (< (count result) total) :orange)
                       :font (util/derived-font :style :plain))
             (Box/createRigidArea (to-dimension [10 :by 0]))
             (ui/toolbar
              :floatable? false
              :items [(ui/button :action filter-action)
                      (ui/button :action export-action)
                      (ui/button :action query-action)])])))

(def opened-article-id
  (atom nil))

(defn handle-mouse-pressed
  [result ^MouseEvent e]
  (when (= 2 (.getClickCount e))
    (let [table   (.getSource e)
          adapter (.getComponentAdapter ^JXTable table)
          row     (.rowAtPoint ^JXTable table ^Point (.getPoint e))
          row     (cond->> row
                    (<= 0 row) (.convertRowIndexToModel
                                ^JXTable$TableAdapter adapter))]
      (when (<= 0 row)
        (let [{:keys [id]} (nth result row)]
          (workspace/open-article id)
          (reset! opened-article-id id))))))

(defn render-result
  [{:keys [result] :as data}]
  (let [model        (map result->table-model result)
        highlighters (into-array Highlighter [(create-highlighter model)])
        table        (uix/table-x
                      :model [:rows model :columns result-table-columns]
                      :listen [:mouse-pressed #(handle-mouse-pressed model %)
                               :component-resized resize-columns])]
    (ui/border-panel
     :class :result
     :user-data data
     :north (render-result-summary data)
     :center (ui/scrollable
              (doto table
                (.setAutoResizeMode JTable/AUTO_RESIZE_ALL_COLUMNS)
                (.setHighlighters highlighters))))))

(def panel
  (JideTabbedPane. JTabbedPane/BOTTOM))

(uib/bind (uib/selection panel)
          (uib/transform #(some-> % :content
                                  (ui/select  [:.result]) (first)
                                  (ui/user-data) :query))
          (uib/value input))

(defn result=
  [a b]
  (= (:query a) (:query b)))

(def list-icon
  (util/icon :list))

(defn update-results
  [queries]
  (let [ids      (into #{} (map :id queries))
        results  (ui/select panel [:.result])
        existing (into #{} (map (comp :id ui/user-data)) results)
        added    (remove (comp existing :id) queries)
        removed  (filter (comp #(nil? (ids %)) :id ui/user-data) results)]
    (doseq [removed removed] (.remove panel removed))
    (doseq [{title :query :as added} (reverse added)]
      (let [tab (render-result added)]
        (.insertTab panel title list-icon tab title 0)
        (ui/selection! panel tab)
        (workspace/show-view :results)
        (ui/value! input title)))))


(uib/subscribe (uib/bind client/queries (uib/notify-now)) update-results)
