(ns zdl-lex-client.help
  (:require [clojure.java.io :as io]
            [seesaw.core :as ui]
            [zdl-lex-client.icon :as icon])
  (:import java.awt.Color))

(def action
  (ui/action
   :name "Hilfe"
   :icon icon/gmd-help
   :handler (fn [_]
              (let [help-text (doto
                                  (ui/styled-text
                                   :editable? false
                                   :wrap-lines? true
                                   :background :white
                                   :margin 10)
                                (.setContentType "text/html")
                                (.setText (-> "help.html" io/resource slurp)))
                    scrollpane (ui/scrollable help-text)
                    scrollpane-viewport (.getViewport scrollpane)
                    scrollpane-viewport (.setBackground scrollpane-viewport
                                                        Color/WHITE)]
                (ui/scroll! help-text :to :top)
                (-> (ui/dialog
                     :title "Hilfe"
                     :content scrollpane
                     :modal? true
                     :size [800 :by 600])
                    ui/show!)))))
(comment
  (ui/invoke-later
   (-> (ui/frame :title "Hilfe"
                 :content (ui/button :action action))
       ui/pack!
       ui/show!)))
