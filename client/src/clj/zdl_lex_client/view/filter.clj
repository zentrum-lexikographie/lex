(ns zdl-lex-client.view.filter
  (:require [clojure.string :as str]
            [lucene-query.core :as lucene]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.bus :as bus]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [tick.format :as tf]))

(def facet-title
  {:status "Status"
   :authors "Autor"
   :sources "Quelle"
   :type "Typ"
   :tranche "Tranche"
   :timestamp "Zeitstempel"})

(def facet-field
  {:status "status"
   :authors "autor"
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
                :font {:style :plain}
                :border 5)))

(defn- render-facet-list-entry [this {:keys [value]}]
  (let [[v n] value]
    (ui/config! this
                :text (str v " (" n ")")
                :font {:style :plain}
                :border 5)))

(def facet-lists
  (for [k [:status :authors :sources :type :tranche :timestamp]]
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
                    facet-lists)
               (ui/horizontal-panel :items))
        help (->> (str "<html>"
                       "<b>Tipp:</b> "
                       "Auswahl mehrerer Einträge einer Liste mit &lt;Strg&gt;."
                       "</html>")
                  (ui/label :font {:size 10} :border 5 :text))
        content (ui/border-panel :center lists :south help)
        options [(ui/button :text "Filtern" :listen [:action do-filter!])
                 (ui/button :text "Abbrechen" :listen [:action cancel-filter!])]]
    (ui/dialog :title "Suchfilter"
               :type :question
               :size [800 :by 800]
               :content content
               :options options)))

(def action
  (ui/action :name "Filter"
             :icon icon/gmd-filter
             :enabled? false
             :handler (fn [_]
                        (reset-filter!)
                        (-> dialog ui/pack! ui/show!))))


(defstate action-enabled?
  :start (uib/bind (bus/bind :search-result)
                   (uib/transform :facets)
                   (uib/transform some?)
                   (uib/property action :enabled?))
  :stop (action-enabled?))

(comment
  (lucene/str->ast "autor:rast")
  (lucene/str->ast "datum:[2019-01-01 TO *]")
  (-> dialog ui/pack! ui/show!))
