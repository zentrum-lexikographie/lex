(ns zdl.lex.client.filter
  (:require
   [clojure.string :as str]
   [seesaw.core :as ui]
   [zdl.lex.bus :as bus]
   [zdl.lex.client.font :as client.font]
   [zdl.lex.client.util :as client.util]
   [zdl.lex.lucene :as lucene]
   [zdl.lex.timestamp :as ts]))

(def facet-title
  {:status "Status"
   :author "Autor"
   :editor "Redakteur"
   :errors "Fehler"
   :source "Quelle"
   :type "Typ"
   :tranche "Tranche"
   :provenance "Ersterfassung"
   :timestamp "Zeitstempel"})

(def facet-field
  {:status "status"
   :author "autor"
   :editor "red"
   :errors "fehler"
   :source "quelle"
   :type "typ"
   :tranche "tranche"
   :provenance "ersterfassung"
   :timestamp "datum"})

(def ^:private german-date-formatter
  (java.time.format.DateTimeFormatter/ofPattern "dd.MM.yy"))

(defn- parse-ts-facet-value
  [^String s]
  (->> (.. java.time.format.DateTimeFormatter/ISO_ZONED_DATE_TIME
           (parse s))
       (java.time.LocalDateTime/from)))

(defn- render-ts-facet-value
  [^java.time.LocalDateTime dt]
  (.. dt (atZone (java.time.ZoneId/of "Europe/Berlin"))
      (format german-date-formatter)))

(defn- render-ts-facet-list-entry [this {:keys [value]}]
  (let [[v n] value
        v (str "ab " (-> v parse-ts-facet-value render-ts-facet-value))]
    (ui/config! this
                :text (str v " (" n ")")
                :font (client.font/derived :style :plain)
                :border 5)))

(defn- render-facet-list-entry [this {:keys [value]}]
  (let [[v n] value]
    (ui/config! this
                :text (str v " (" n ")")
                :font (client.font/derived :style :plain)
                :border 5)))

(defn facet-lists->query [facet-lists]
  (->>
   (for [fl facet-lists
         :let [k (ui/user-data fl)
               vs (map first (ui/selection fl {:multi? true}))]
         :when (seq vs)]
     [:clause
      [:field [:term (facet-field k)]]
      (condp = k
        :timestamp
        [:range
         "["
         [:term (-> vs first parse-ts-facet-value ts/format)]
         [:all "*"]
         "]"]
        (let [vs (map (fn [v] [:value [:term v]]) vs)]
          (if (= 1 (count vs))
            (first vs)
            [:sub-query (->> (map (fn [v] [:clause v]) vs)
                             (interpose [:or]) (cons :query) (vec))])))])
   (interpose [:and])
   (cons :query)
   (vec)
   (lucene/ast->str)))

(defn dispose! [e]
  (some-> e ui/to-root ui/dispose!))

(defn filter! [query facet-lists e]
  (some->>
   (remove empty? [(facet-lists->query facet-lists) query])
   (str/join " AND ")
   (not-empty)
   (array-map :query)
   (bus/publish! #{:search-request}))
  (dispose! e))

(defn open-dialog
  [{:keys [query facets]} & args]
  (let [result->list-model #(->> (or (some-> facets %) {})
                                 (into [])
                                 (sort-by first))
        facet-lists (for [k [:status :author :editor :timestamp
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
        lists (->>
               (map #(ui/scrollable % :border [5 (-> % ui/user-data facet-title)])
                    facet-lists))
        help (->> (str "<html>"
                       "<b>Tipp:</b> "
                       "Auswahl mehrerer Einträge einer Liste mit &lt;Strg&gt;."
                       "</html>")
                  (ui/label :border 5 :text))
        content (ui/border-panel
                 :center (ui/grid-panel
                          :rows 2
                          :columns 5
                          :items lists
                          :hgap 5
                          :vgap 5)
                 :south help
                 :hgap 5
                 :vgap 5)
        options [(ui/button :text "Filtern"
                            :listen [:action (partial filter! query facet-lists)])
                 (ui/button :text "Abbrechen"
                            :listen [:action dispose!])]]
    (-> (ui/dialog :title "Suchfilter"
                   :type :question
                   :parent (some-> args first ui/to-root)
                   :size (client.util/clip-to-screen-size [800 :by 600])
                   :content content
                   :options options)
        ui/pack!
        ui/show!
        ui/invoke-later)))

(comment
  (lucene/str->ast "autor:rast")
  (lucene/str->ast "datum:[2019-01-01 TO *]"))
