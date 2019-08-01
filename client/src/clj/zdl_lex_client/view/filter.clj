(ns zdl-lex-client.view.filter
  (:require [clojure.string :as str]
            [lucene-query.core :as lucene]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.search :as search]))

(def facet-title
  {:status "Status"
   :authors "Autor"
   :sources "Quelle"
   :type "Typ"
   :tranche "Tranche"})

(def facet-field
  {:status "status"
   :authors "autor"
   :sources "quelle"
   :type "typ"
   :tranche "tranche"})

(defn facet->model [result k]
  (->> (or (some-> result :facets k) {})
       (into []) (sort-by first)))

(defn- render-facet-list-entry [this {:keys [value]}]
  (let [[v n] value]
    (ui/config! this
                :text (str v " (" n ")")
                :font {:style :plain}
                :border 5)))

(defn- create-facet-list [k]
  (let [list (ui/listbox :model (facet->model @search/current-result k)
                         :selection-mode :multi-interval
                         :renderer render-facet-list-entry
                         :class :facet-list
                         :user-data k)]
    (uib/bind search/current-result
              (uib/transform #(facet->model % k))
              (uib/property list :model))
    (ui/scrollable list :border [5 (facet-title k)])))

(def facet-lists
  [(create-facet-list :authors)
   (create-facet-list :status)
   (create-facet-list :sources)
   (create-facet-list :type)
   (create-facet-list :tranche)])

(defn get-filter-values []
  (->>
   (for [fl facet-lists
         fl (ui/select fl [:.facet-list])
         :let [k (ui/user-data fl)
               vs (->> (ui/selection fl {:multi? true})
                      (map first )
                      (map (fn [v] [:value [:term v]])))]
         :when (not (empty? vs))]
     [:clause
      [:field [:term (facet-field k)]]
      (if (= 1 (count vs))
        (first vs)
        [:sub-query (->> (map (fn [v] [:clause v]) vs)
                         (interpose [:or]) (cons :query) (vec))])])
   (interpose [:and]) (cons :query) (vec)))


(defn do-filter! [e]
  (some->> (get-filter-values)
           (lucene/ast->str)
           (vector @search/query)
           (remove empty?)
           (str/join " AND ")
           (not-empty)
           (search/request))
  (ui/return-from-dialog e :filter))

(defn reset-filter! [& _]
  (doseq [fl facet-lists
          fl (ui/select fl [:.facet-list])]
    (ui/selection! fl [] {:multi? true})))

(defn cancel-filter! [e]
  (ui/return-from-dialog e :cancel))

(defn create-dialog []
  (reset-filter!)
  (ui/dialog :title "Suchfilter"
             :type :question
             :size [800 :by 800]
             :content (ui/horizontal-panel :items facet-lists)
             :options [(ui/button :text "Filtern"
                                  :listen [:action do-filter!])
                       (ui/button :text "Zurücksetzen"
                                  :listen [:action reset-filter!])
                       (ui/button :text "Abbrechen"
                                  :listen [:action cancel-filter!])]))

(comment
  (lucene/str->ast "autor:rast")
  (-> (create-dialog) ui/pack! ui/show!))
