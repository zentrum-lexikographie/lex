(ns zdl-lex-client.view.filter
  (:require [clojure.string :as str]
            [lucene-query.core :as lucene]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [seesaw.forms :as forms]
            [tick.alpha.api :as t]
            [tick.format :as tf]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.font :as font]
            [zdl-lex-client.search :as search])
  (:import com.jgoodies.forms.layout.RowSpec))

(def facet-title
  {:status "Status"
   :authors "Autor"
   :editors "Redakteur"
   :sources "Quelle"
   :type "Typ"
   :tranche "Tranche"
   :timestamp "Zeitstempel"})

(def facet-field
  {:status "status"
   :authors "autor"
   :editors "red"
   :sources "quelle"
   :type "typ"
   :tranche "tranche"
   :timestamp "datum"})

(defn- render-ts-facet-list-entry [this {:keys [value]}]
  (let [[v n] value
        v (str "ab " (t/format (tf/formatter "dd.MM.yy")
                               (t/in (t/parse v) "Europe/Berlin")))]
    (ui/config! this
                :text (str v " (" n ")")
                :font (font/derived :style :plain)
                :border 5)))

(defn- render-facet-list-entry [this {:keys [value]}]
  (let [[v n] value]
    (ui/config! this
                :text (str v " (" n ")")
                :font (font/derived :style :plain)
                :border 5)))

(defn facet-lists->query [facet-lists]
  (->>
   (for [fl facet-lists
         :let [k (ui/user-data fl)
               vs (map first (ui/selection fl {:multi? true}))]
         :when (not (empty? vs))]
     [:clause
      [:field [:term (facet-field k)]]
      (condp = k
        :timestamp
        [:range
         "["
         [:term (t/format :iso-date (-> vs first t/parse t/date))]
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
   not-empty
   search/request)
  (dispose! e))

(defn open-dialog [{:keys [query facets]} & args]
  (let [result->list-model #(->> (or (some-> facets %) {})
                                 (into [])
                                 (sort-by first))
        facet-lists (for [k [:status :authors :editors :timestamp
                             :sources :tranche :type]]
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
        content (forms/forms-panel
                 "pref, 4dlu, pref, 4dlu, pref, 4dlu, pref"
                 :default-row-spec (RowSpec. "fill:pref")
                 :default-dialog-border? true
                 :items (concat lists [(forms/next-line) (forms/span help 7)]))
        options [(ui/button :text "Filtern"
                            :listen [:action (partial filter! query facet-lists)])
                 (ui/button :text "Abbrechen"
                            :listen [:action dispose!])]]
    (-> (ui/dialog :title "Suchfilter"
                   :type :question
                   :parent (some-> args first ui/to-root)
                   :size [800 :by 800]
                   :content content
                   :options options)
        ui/pack!
        ui/show!)))

(comment
  (lucene/str->ast "autor:rast")
  (lucene/str->ast "datum:[2019-01-01 TO *]"))
