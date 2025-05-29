(ns zdl.lex.ui.links
  (:require
   [clojure.string :as str]
   [seesaw.bind :as uib]
   [seesaw.border :refer [empty-border line-border]]
   [seesaw.core :as ui]
   [seesaw.mig :as mig]
   [zdl.lex.article :as article]
   [zdl.lex.client :as client]
   [zdl.lex.oxygen.workspace :as workspace]
   [zdl.lex.ui.util :as util]))

(defn render-link
  [{:keys [id status pos incoming? outgoing?] :as link}]
  (let [bidi? (and incoming? outgoing?)
        label ui/label
        text  (partial label :font (util/derived-font :style :plain))]
    (mig/mig-panel
     :cursor :hand
     :background :snow
     :border [5
              (line-border :color (article/status->color status) :left 10)
              (line-border :thickness 5 :color :white)]
     :items [[(label :icon (cond
                             bidi? (util/icon :compare-arrows)
                             incoming? (util/icon :arrow-back)
                             outgoing? (util/icon :arrow-forward))
                     :text (cond-> (-> link :forms first) pos (str " (" pos ")"))
                     :tip id
                     :border [(empty-border :bottom 2)
                              (line-border :color :black :bottom 1)])
              "span 2, width ::(100% - 80), wrap"]
             [(label :text "Status")] [(text :text status) "wrap"]
             [(label :text "Typ")] [(text :text (link :type)) "wrap"]
             [(label :text "Quelle")] [(text :text (link :source)) "wrap"]
             [(label :text "Zeitstempel")] [(text :text (link :last-modified))]])))

(def missing-anchors-label
  (ui/text :multi-line? true
           :editable? false
           :wrap-lines? true
           :text ""
           :foreground :snow
           :background :red
           :border (line-border :thickness 5 :color :red)
           :font (util/derived-font :style :bold)
           :visible? false))

(defn render-missing-anchors
  [missing-anchors]
  (str "Fehlende Verweisziele: "
       (str/join ", " (map #(str "„" % "”") missing-anchors))))

(def link-list
  (ui/listbox
   :model    []
   :listen   [:selection (util/do-on-selection (comp workspace/open-article :id))]
   :renderer (proxy [javax.swing.DefaultListCellRenderer] []
               (getListCellRendererComponent
                 [component value index selected? focus?]
                 (render-link value)))))

(def pane
  (ui/border-panel
   :north missing-anchors-label
   :center (ui/scrollable link-list)))

(uib/bind
 (uib/funnel client/active-article client/articles)
 (uib/transform
  (fn [_] (when-let [id @client/active-article]
            (some-> (@client/articles id) ::client/links))))
 (uib/tee
  (uib/bind
   (uib/transform :links)
   (uib/property link-list :model))
  (uib/bind
   (uib/transform :missing)
   (uib/tee
    (uib/bind
     (uib/transform seq)
     (uib/property missing-anchors-label :visible?))
    (uib/bind
     (uib/transform render-missing-anchors)
     (uib/property missing-anchors-label :text))))))
