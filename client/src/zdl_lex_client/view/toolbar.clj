(ns zdl-lex-client.view.toolbar
  (:require [clojure.java.io :as io]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.preview :as preview]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.oxygen.validation :as validation]
            [zdl-lex-client.view.article :as article-view]
            [zdl-lex-client.view.search :as search-view]
            [zdl-lex-client.font :as font])
  (:import java.awt.Color
           ro.sync.exml.workspace.api.standalone.ui.ToolbarButton))

(def ^:private search-all-action
  (ui/action :name "Alle Artikel"
             :icon icon/gmd-all
             :handler (fn [_] (search/request "*"))))

(defn show-help [& args]
  (let [help-text (ui/styled-text :editable? false :wrap-lines? true
                                  :background :white :margin 10)
        scrollpane (ui/scrollable help-text)]
    (.. scrollpane (getViewport) (setBackground Color/WHITE))
    (doto help-text
      (.setContentType "text/html")
      (.setText (-> "help.html" io/resource slurp))
      (ui/scroll! :to :top))
    (-> (ui/dialog :title "Hilfe"
                   :content scrollpane
                   :parent (some-> args first ui/to-root)
                   :modal? true
                   :size [800 :by 600])
        (ui/show!))))

(def ^:private show-help-action
  (ui/action :name "Hilfe" :icon icon/gmd-help :handler show-help))

(defn components []
  (let [status-label (ui/label :text "–" :border 5)
        status-label-text (uib/bind (bus/bind :status)
                                    (uib/transform :user)
                                    (uib/property status-label :text))]
    [icon/logo
     status-label
     (search-view/input)
     (ToolbarButton. search-view/action false)
     (ToolbarButton. search-all-action false)
     (ToolbarButton. article-view/create-action false)
     (ToolbarButton. show-help-action false)
     (ToolbarButton. preview/action false)
     (ToolbarButton. validation/activation-action false)]))

(defn widget []
  (ui/toolbar :floatable? false
              :orientation :horizontal
              :items (components)))
