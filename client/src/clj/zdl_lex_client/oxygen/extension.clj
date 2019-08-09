(ns zdl-lex-client.oxygen.extension
  (:gen-class
   :name de.zdl.oxygen.Extension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension])
  (:require [mount.core :as mount]
            [zdl-lex-client.editors :as editors]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.repl :as repl]
            [zdl-lex-client.view.article :as article-view]
            [zdl-lex-client.view.results :as results-view]
            [zdl-lex-client.view.toolbar :as toolbar]
            [zdl-lex-client.workspace :as ws])
  (:import javax.swing.JComponent
           [ro.sync.exml.workspace.api.standalone ToolbarComponentsCustomizer ViewComponentCustomizer]))

(defn -applicationStarted [this app-ws]
  (mount/start-with {#'ws/instance app-ws})
  (.addViewComponentCustomizer
     app-ws
     (proxy [ViewComponentCustomizer] []
       (customizeView [viewInfo]
         (condp = (.getViewID viewInfo)
           (ws/views :results)
           (doto viewInfo
              (.setTitle "ZDL/DWDS – Suchergebnisse")
              (.setIcon icon/gmd-result)
              (.setComponent results-view/tabbed-pane))
           (ws/views :article)
           (doto viewInfo
             (.setTitle "ZDL/DWDS – Artikel")
             (.setIcon icon/gmd-details)
             (.setComponent article-view/panel))
           viewInfo))))
  (.addToolbarComponentsCustomizer
   app-ws
   (proxy [ToolbarComponentsCustomizer] []
     (customizeToolbar [toolbarInfo]
       (condp = (.getToolbarID toolbarInfo)
         (ws/views :toolbar)
         (doto toolbarInfo
           (.setTitle "ZDL/DWDS")
           (.setComponents (into-array JComponent toolbar/components)))
         toolbarInfo)))))

(defn -applicationClosing [this]
  (mount/stop)
  true)

(comment
  repl/server
  editors/listeners)
