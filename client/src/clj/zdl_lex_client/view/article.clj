(ns zdl-lex-client.view.article
  (:require [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.bus :as bus]))

(def active (ui/label :text "-"))

(defstate active-text
  :start (uib/bind (bus/bind :editor-active)
                   (uib/transform
                    #(if (second %) (some-> % first str article/url->id) "-"))
                   (uib/property active :text))
  :stop (active-text))

(def panel (ui/scrollable (ui/border-panel :center active)))
