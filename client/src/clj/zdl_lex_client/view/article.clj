(ns zdl-lex-client.view.article
  (:require [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.bus :as bus]))

(def active (ui/text :multi-line? true
                     :editable? false
                     :wrap-lines? true
                     :margin 5
                     :text "-"))

(defstate active-text
  :start (uib/bind (bus/bind :article)
                   (uib/transform str)
                   (uib/property active :text))
  :stop (active-text))

(def panel (ui/scrollable (ui/border-panel :center active)))
