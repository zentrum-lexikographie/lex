(ns zdl-lex-client.view.toolbar
  (:require [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.view.filter :as filter-view]
            [zdl-lex-client.view.search :as search-view]
            [zdl-lex-client.search :as search]
            [taoensso.timbre :as timbre])
  (:import java.awt.Color
           ro.sync.exml.workspace.api.standalone.ui.ToolbarButton))


(def ^:private status-label (ui/label :text "–" :border 5))

(defstate status-label-text
  :start (uib/bind (bus/bind :status)
                   (uib/transform :user)
                   (uib/property status-label :text))
  :stop (status-label-text))

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


(def ^:private search-all-action
  (ui/action :name "Alle Artikel"
             :icon icon/gmd-all
             :handler (fn [_] (search/request "*"))))

(def ^:private help-text
  (let [help-text (ui/styled-text :editable? false :wrap-lines? true
                                  :background :white :margin 10)
        scrollpane (ui/scrollable help-text)]
    (.. scrollpane (getViewport) (setBackground Color/WHITE))
    (doto help-text
      (.setContentType "text/html")
      (.setText (-> "help.html" io/resource slurp))
      (ui/scroll! :to :top))
    scrollpane))

(def ^:private show-help-action
  (ui/action
   :name "Hilfe"
   :icon icon/gmd-help
   :handler (fn [_]
              (-> (ui/dialog :title "Hilfe" :content help-text
                             :modal? true :size [800 :by 600])
                  (ui/show!)))))

(def components
  [icon/logo
   status-label
   search-view/input
   (ToolbarButton. search-view/action false)
   (ToolbarButton. search-all-action false)
   (ToolbarButton. filter-view/action false)
   (ToolbarButton. show-help-action false)
   (ToolbarButton. create-article-action false)])

(def widget
  (ui/toolbar :floatable? false
              :orientation :horizontal
              :items components))
