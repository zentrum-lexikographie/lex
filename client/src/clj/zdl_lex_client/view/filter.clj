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
            [zdl-lex-client.icon :as icon]
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

(defn facet->model [result k]
  (->> (or (some-> result :facets k) {})
       (into []) (sort-by first)))

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

(def facet-lists
  (for [k [:status :authors :editors :timestamp :sources :tranche :type]]
    (condp = k
      :timestamp
      (ui/listbox :model []
                  :selection-mode :single
                  :renderer render-ts-facet-list-entry
                  :class :facet-list
                  :user-data k)
      (ui/listbox :model []
                  :selection-mode :multi-interval
                  :renderer render-facet-list-entry
                  :class :facet-list
                  :user-data k))))

(defstate result->facets
  :start (doall
          (for [fl facet-lists
                :let [k (ui/user-data fl)]]
            (uib/bind (bus/bind :search-result)
                      (uib/transform #(facet->model % k))
                      (uib/property fl :model))))
  :stop (doseq [b result->facets] (b)))

(defn facet-lists->ast []
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
   (interpose [:and]) (cons :query) (vec)))

(defn do-filter!
  ([e]
   (do-filter! e (-> (facet-lists->ast) (lucene/ast->str))))
  ([e filter]
   (some->> [filter @search/query] (remove empty?) (str/join " AND ")
            not-empty search/request)
   (when e (ui/dispose! (ui/to-root e)))))

(defn reset-filter! []
  (doseq [fl facet-lists]
    (ui/selection! fl [] {:multi? true})))

(defn cancel-filter! [e]
  (ui/dispose! (ui/to-root e)))

(def dialog
  (let [lists (->>
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
        options [(ui/button :text "Filtern" :listen [:action do-filter!])
                 (ui/button :text "Abbrechen" :listen [:action cancel-filter!])]]
    (ui/dialog :title "Suchfilter"
               :type :question
               :size [800 :by 800]
               :content content
               :options options)))

(defn open-dialog []
  (reset-filter!)
  (-> dialog ui/pack! ui/show!))

(def action
  (ui/action :icon icon/gmd-filter
             :handler (fn [_]
                        )))

(comment
  (lucene/str->ast "autor:rast")
  (lucene/str->ast "datum:[2019-01-01 TO *]")
  (-> dialog ui/pack! ui/show!))
