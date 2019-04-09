(ns zdl-lex-client.quicksearch
  (:require [seesaw.core :as ui]
            [seesaw.border :refer [empty-border line-border]]
            [seesaw.swingx :as uix]
            [seesaw.mig :as uim]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [clojure.string :as str])
  (:import [javax.swing JLabel]
           [com.jidesoft.hints AbstractListIntelliHints]))

(defn- suggestion-text [{:keys [suggestion pos type definitions 
                         status id last-modified]}]
  (let [suggestion (-> suggestion
                       (str/replace "<b>" "<u>")
                       (str/replace "</b>" "</u>"))
        suggestion (str "<font size=\"+1\">" suggestion "</font>")

        pos (-> pos first (or "?"))
        pos (str "&lt;" pos "&gt;")

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
              :font "Dialog-12"
              :border [5 (line-border :color (suggestion-color value)
                                      :left 5) 5]))

(defn quick-search []
  (let [input (ui/text :columns 40 :font "18" :border 5)]
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
        (timbre/info hint)))
    input))

(defn -main []
  (ui/invoke-later
   (-> (ui/frame :title "Quick Search" :content (quick-search))
       ui/pack!
       ui/show!)))
