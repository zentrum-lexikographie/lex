(ns zdl.lex.client.toolbar
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [seesaw.bind :as uib]
   [seesaw.core :as ui]
   [zdl.lex.bus :as bus]
   [zdl.lex.client.article :as client.article]
   [zdl.lex.client.http :as client.http]
   [zdl.lex.client.icon :as client.icon]
   [zdl.lex.client.search :as client.search]
   [zdl.lex.client.validation :as client.validation])
  (:import
   (java.awt Color)
   (ro.sync.exml.workspace.api.standalone.ui ToolbarButton)))

(defn auth->status-label
  [[user _]]
  (or user "<nicht angemeldet>"))

(def status-label
  (ui/label :text (auth->status-label @client.http/*auth*) :border 5))

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

(def preview-action
  (ui/action :name "Artikelvorschau"
             :icon client.icon/gmd-web
             :handler (fn [_]
                        (bus/publish! #{:preview-article} {:preview? true}))))

(def components
  [client.icon/logo
   status-label
   client.search/input
   (ToolbarButton. client.search/action false)
   (ToolbarButton. search-all-action false)
   (ToolbarButton. client.article/create-action false)
   (ToolbarButton. show-help-action false)
   (ToolbarButton. preview-action false)
   (ToolbarButton. client.validation/action false)])

(def widget
  (ui/toolbar :floatable? false
              :orientation :horizontal
              :items components))

(defmethod ig/init-key ::events
  [_ _]
  [(uib/bind
    client.http/*auth*
    (uib/transform auth->status-label)
    (uib/property status-label :text))])

(defmethod ig/halt-key! ::events
  [_ callbacks]
  (doseq [callback callbacks] (callback)))
