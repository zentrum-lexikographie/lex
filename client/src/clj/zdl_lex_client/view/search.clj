(ns zdl-lex-client.view.search
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.behave :refer [when-focused-select-all]]
            [seesaw.bind :as uib]
            [seesaw.border :refer [line-border]]
            [seesaw.core :as ui]
            [zdl-lex-common.article :as article]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.workspace :as ws])
  (:import com.jidesoft.hints.AbstractListIntelliHints))

(def action
  (ui/action
   :name "Suchen"
   :icon icon/gmd-search
   :handler (fn [_]
              (let [valid? @search/query-valid? q @search/query]
                (when valid? (search/request q))))))

(def input
  (doto (ui/text :columns 40 :action action)
    (when-focused-select-all)))


(defstate input-value
  :start (uib/bind search/query input search/query)
  :stop (input-value))

(defstate input-validity
  :start (uib/bind search/query-valid?
                   (uib/transform #(if % :black :red))
                   (uib/property input :foreground))
  :stop (input-validity))

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

(def ^:private input-hints
  (proxy [AbstractListIntelliHints] [input]
    (createList []
      (ui/config! (proxy-super createList)
                  :background :white
                  :renderer render-suggestion-list-entry))
    (updateHints [ctx]
      (let [q (str ctx)
            suggestions? (< 1 (count q))
            suggestions (if suggestions? (-> q http/suggest-forms :result))
            suggestions (or suggestions [])]
        (proxy-super setListData (into-array Object suggestions))
        (not (empty? suggestions))))
    (acceptHint [{:keys [id]}]
      (ws/open-article ws/instance id))))

