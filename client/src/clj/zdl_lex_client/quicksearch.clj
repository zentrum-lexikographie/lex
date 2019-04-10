(ns zdl-lex-client.quicksearch
  (:require [seesaw.core :as ui]
            [seesaw.border :refer [empty-border line-border]]
            [seesaw.mig :as uim]
            [zdl-lex-client.http :as http]
            [clojure.string :as str])
  (:import [com.jidesoft.hints AbstractListIntelliHints]))

(def ^{:private true} input-len 40)

(defn- suggestion-text [{:keys [suggestion pos type definitions 
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
        definition (subs definition 0 (min input-len definition-length))
        definition (str definition (if (< input-len definition-length) "…" ""))
        definition (str "<i>" definition "</i>")

        html (str/join "<br>" (remove nil? [title subtitle definition]))]

    (str "<html>" html "</html>")))

(defn- suggestion-color [{:keys [status]}]
  (cond
    (= "Red-f" status) :green
    (= "Red-2" status) :yellow
    (= "Artikelrumpf" status) :lightgrey
    (.startsWith status "Lex-") :orange
    (.endsWith status "-zurückgewiesen") :red
    :else :white))

(defn- render-suggestion [this {:keys [value index selected? focus?]}]
  (ui/config! this
              :text (suggestion-text value)
              :font :sans-serif
              :border [5 (line-border :color (suggestion-color value)
                                      :right 10) 5]))

(defn input [f]
  (let [action-handler (fn [e]
                         (let [input (.getSource e)]
                           (.selectAll input)
                           (-> input ui/value f)))
        input (ui/text :columns input-len
                       :action (ui/action :handler action-handler))]
    (proxy [AbstractListIntelliHints] [input]
      (createList []
        (let [list (proxy-super createList)]
          (ui/config! list
                      :background :white
                      :renderer render-suggestion)))
      (updateHints [ctx]
        (let [q (str ctx)
              suggestions? (< 1 (count q))
              suggestions (if suggestions? (-> q http/form-suggestions :result))
              suggestions (or suggestions [])]
          (proxy-super setListData (into-array Object suggestions))
          (-> suggestions empty? not)))
      (acceptHint [hint]
        (proxy-super acceptHint (-> hint :suggestion (str/replace #"</?b>" "")))
        (.selectAll input)
        (f hint)))
    input))

;; (ui/invoke-later (-> (ui/frame :title "Quick Search" :content (input println)) ui/pack! ui/show!))
