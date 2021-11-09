(ns zdl.lex.client.toolbar
  (:require [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl.lex.bus :as bus]
            [zdl.lex.client.icon :as client.icon]
            [zdl.lex.client.http :as client.http]
            [zdl.lex.client.article :as client.article]
            [zdl.lex.client.search :as client.search])
  (:import java.awt.Color
           ro.sync.exml.workspace.api.standalone.ui.ToolbarButton))

(def search-all-action
  (ui/action :name "Alle Artikel"
             :icon client.icon/gmd-all
             :handler (fn [_]
                        (bus/publish! #{:search-request} {:query "*"}))))

(def help-pane
  (doto (ui/scrollable
         (doto (ui/styled-text :editable? false
                               :wrap-lines? true
                               :background :white
                               :margin 10)
           (.setContentType "text/html")
           (.setText (slurp (io/resource "help.html")))))
    (.. (getViewport) (setBackground Color/WHITE))))

(defn show-help
  [& args]
  (ui/invoke-later
   (ui/scroll! help-pane :to :top)
   (ui/show!
    (ui/dialog :title "Hilfe"
               :content help-pane
               :parent (some-> args first ui/to-root)
               :modal? true
               :size [800 :by 600]))))

(def show-help-action
  (ui/action :name "Hilfe"
             :icon client.icon/gmd-help
             :handler show-help))

(defn auth->status-label
  [[user _]]
  (or user "<nicht angemeldet>"))

(def status-label
  (ui/label :text (auth->status-label @client.http/*auth*) :border 5))

(defstate status-label-text
  :start (uib/bind
          client.http/*auth*
          (uib/transform auth->status-label)
          (uib/property status-label :text))
  :stop (status-label-text))

(def preview-action
  (ui/action :name "Artikelvorschau"
             :icon client.icon/gmd-web
             :handler (fn [_]
                        (bus/publish! #{:preview-article} {:preview? true}))))

(def validation-active?
  (atom false))

(def validation-action
  (ui/action :name "Typographieprüfung"
             :tip "Typographieprüfung (deaktiviert)"
             :icon client.icon/gmd-error-outline
             :handler (fn [_]
                        (let [validate? (swap! validation-active? not)]
                          (bus/publish! #{:validate?} {:validate? validate?})))))

(defstate validation-action-states
  :start
  (uib/bind
   validation-active?
   (uib/tee
    (uib/bind
     (uib/transform #(if %
                       client.icon/gmd-error
                       client.icon/gmd-error-outline))
     (uib/property validation-action :icon))
    (uib/bind
     (uib/transform #(str "Typographieprüfung (" (when-not % "de") "aktiviert)"))
     (uib/property validation-action :tip))))
  :stop
  (validation-action-states))

(def components
  [client.icon/logo
   status-label
   client.search/input
   (ToolbarButton. client.search/action false)
   (ToolbarButton. search-all-action false)
   (ToolbarButton. client.article/create-action false)
   (ToolbarButton. show-help-action false)
   (ToolbarButton. preview-action false)
   (ToolbarButton. validation-action false)])

(def widget
  (ui/toolbar :floatable? false
              :orientation :horizontal
              :items components))
