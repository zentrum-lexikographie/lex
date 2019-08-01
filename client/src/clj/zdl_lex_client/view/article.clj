(ns zdl-lex-client.view.article
  (:require [seesaw.core :as ui]
            [zdl-lex-client.editors :as editors]
            [seesaw.bind :as uib]
            [zdl-lex-client.article :as article]))

(defonce ^:private active
  (let [label (ui/label :text "-")]
    (uib/bind
     editors/active
     (uib/transform #(some-> % article/url->id))
     (uib/transform #(or % "-"))
     (uib/property label :text))
    label))

(def panel (ui/scrollable (ui/border-panel :center active)))
