(ns zdl-lex-client.view.toolbar
  (:require [clojure.java.io :as io]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.status :as status]
            [zdl-lex-client.view.search :as search-view])
  (:import java.awt.Color
           ro.sync.exml.workspace.api.standalone.ui.ToolbarButton))


(defonce ^:private status-label
  (let [label (ui/label :text "–" :border 5)]
    (uib/bind status/current
              (uib/transform :user)
              (uib/property label :text))
    label))

(def ^:private create-article-action
  (ui/action
   :name "Artikel erstellen"
   :icon icon/gmd-add
   :handler
   (fn [_]
     (->
      (ui/dialog :content "Neuer Artikel!"
                 :option-type :ok-cancel
                 :modal? true
                 :success-fn #(ui/dispose! %)
                 :cancel-fn #(ui/dispose! %))
      (ui/pack!)
      (ui/show!)))))

(def ^:private help-text
  (doto
      (ui/styled-text
       :editable? false
       :wrap-lines? true
       :background :white
       :margin 10)
    (.setContentType "text/html")
    (.setText (-> "help.html" io/resource slurp))))

(def ^:private show-help-action
  (ui/action
   :name "Hilfe"
   :icon icon/gmd-help
   :handler (fn [_]
              (let [scrollpane (ui/scrollable help-text)
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
(def components
  [icon/logo
   status-label
   search-view/input
   (ToolbarButton. search-view/action false)
   (ToolbarButton. show-help-action false)
   (ToolbarButton. create-article-action false)])

(def widget
  (ui/toolbar :floatable? false
              :orientation :horizontal
              :items components))
