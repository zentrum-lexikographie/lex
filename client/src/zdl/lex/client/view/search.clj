(ns zdl.lex.client.view.search
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.behave :refer [when-focused-select-all]]
            [seesaw.bind :as uib]
            [seesaw.border :refer [line-border]]
            [seesaw.core :as ui]
            [zdl.lex.client.bus :as bus]
            [zdl.lex.client.font :as font]
            [zdl.lex.client.http :as http]
            [zdl.lex.client.icon :as icon]
            [zdl.lex.client.search :as search]
            [zdl.lex.client.workspace :as ws]
            [zdl.lex.article :as article]
            [zdl.lex.lucene :as lucene])
  (:import com.jidesoft.hints.AbstractListIntelliHints))

(defn- suggestion->html [{:keys [suggestion pos type definitions
                                 status id last-modified]}]
  (let [suggestion (-> suggestion
                       (str/replace "<b>" "<u>")
                       (str/replace "</b>" "</u>"))
        suggestion (str "<b>" suggestion "</b>")
        pos (-> pos first (or "?"))
        source (re-find #"^[^/]+" id)
        source (str "<font color=\"blue\">[" source "]</font>")
        title (str/join " " (remove nil? [suggestion pos source]))
        subtitle (str/join ", " (remove nil? [type status last-modified]))
        definition (-> definitions first (or "ohne Def."))
        definition-length (count definition)
        definition (subs definition 0 (min 40 definition-length))
        definition (str definition (if (< 40 definition-length) "…" ""))
        definition (str "<i>" definition "</i>")
        html (str/join "<br>" (remove nil? [title subtitle definition]))]
    (str "<html>" html "</html>")))

(defn- suggestion->border [{:keys [status]}]
  [5 (line-border :color (article/status->color status) :right 10) 5])

(defn- render-suggestion-list-entry [this {:keys [value]}]
  (ui/config! this
              :text (suggestion->html value)
              :border (suggestion->border value)))

(def action
  (ui/action
   :name "Suchen"
   :icon icon/gmd-search
   :handler (fn [_]
              (let [q @search/query]
                (when (lucene/valid? q) (search/request q))))))

(def input
  (let [text-input (ui/text :columns 40
                            :action action
                            :font (font/derived :style :plain))]
    (when-focused-select-all text-input)
    (proxy [AbstractListIntelliHints] [text-input]
      (createList []
        (ui/config! (proxy-super createList)
                    :background :white
                    :renderer render-suggestion-list-entry))
      (updateHints [ctx]
        (let [q (str ctx)
              suggestions? (and (< 1 (count q))
                                (re-matches #"^[^\*\?\"/:]+$" q))
              suggestions (if suggestions?
                            (-> q http/suggest-forms :result))
              suggestions (or suggestions [])]
          (proxy-super setListData (into-array Object suggestions))
          (not (empty? suggestions))))
      (acceptHint [{:keys [id]}]
        (ui/config! text-input :text "")
        (ws/open-article ws/instance id)))
    text-input))

(defstate input-updates
  :start
  [(uib/bind input
             search/query)
   (uib/bind input
             (uib/transform #(if (lucene/valid? %) :black :red))
             (uib/property input :foreground))
   (uib/bind (bus/bind [:search-result])
             (uib/transform (comp :query second))
             (uib/filter identity)
             input)]
  :stop
  (doseq [unsubscribe! input-updates] (unsubscribe!)))
