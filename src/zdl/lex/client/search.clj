(ns zdl.lex.client.search
  (:require [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.behave :refer [when-focused-select-all]]
            [seesaw.bind :as uib]
            [seesaw.border :refer [line-border]]
            [seesaw.core :as ui]
            [zdl.lex.article :as article]
            [zdl.lex.bus :as bus]
            [zdl.lex.client.bind :refer [bind->bus]]
            [zdl.lex.client.font :as client.font]
            [zdl.lex.client.icon :as client.icon]
            [zdl.lex.lucene :as lucene]
            [clojure.tools.logging :as log]
            [zdl.lex.client.http :as client.http])
  (:import com.jidesoft.hints.AbstractListIntelliHints))

(defn- suggestion->html
  [{:keys [suggestion pos type definitions status id last-modified]}]
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

(defn- suggestion->border
  [{:keys [status]}]
  [5 (line-border :color (article/status->color status) :right 10) 5])

(defn- render-suggestion-list-entry
  [this {:keys [value]}]
  (ui/config! this
              :text (suggestion->html value)
              :border (suggestion->border value)))

(def query
  (atom ""))

(def action
  (ui/action
   :name "Suchen"
   :icon client.icon/gmd-search
   :handler (fn [_]
              (let [q @query]
                (when (lucene/valid? q)
                  (bus/publish! #{:search-request} {:query q}))))))

(defn suggest?
  [q]
  (and (< 1 (count q)) (re-matches #"^[^\*\?\"/:]+$" q)))

(defn suggest
  [q]
  (try
    (let [request {:method :get
                   :url "index/forms/suggestions"
                   :query-params {:q q}}
          response (client.http/request request)]
      (get-in response [:body :result]))
    (catch Throwable t
      (log/warnf t "Error retrieving suggestions for '%s'" q)
      [])))

(defn enhance-input
  [input]
  (when-focused-select-all input)
  (proxy [AbstractListIntelliHints] [input]
    (createList []
      (ui/config! (proxy-super createList)
                  :background :white
                  :renderer render-suggestion-list-entry))
    (updateHints [ctx]
      (let [q           (str ctx)
            suggestions (and (suggest? q) (suggest q))
            suggestions (or suggestions [])]
        (proxy-super setListData (into-array Object suggestions))
        (some? (seq suggestions))))
    (acceptHint [{:keys [id]}]
      (ui/config! input :text "")
      (bus/publish! #{:open-article} {:id id})))
  input)

(def input
  (enhance-input (ui/text :columns 40
                          :action action
                          :font (client.font/derived :style :plain))))

(defstate input->
  :start (uib/bind
          input
          (uib/tee
           query
           (uib/bind (uib/transform #(if (lucene/valid? %) :black :red))
                     (uib/property input :foreground))))
  :stop (input->))

(defstate ->input
  :start (uib/bind
          (bind->bus #{:search-request :search-result-selected})
          (uib/transform (comp :query second))
          input)
  :stop (->input))
