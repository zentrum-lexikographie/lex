(ns zdl-lex-client.view-extension
  (:gen-class
   :name de.zdl.oxygen.ViewExtension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension])
  (:require [mount.core :as mount]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.metasearch :as metasearch]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.results :as results])
  (:import javax.swing.JComponent
           [ro.sync.exml.workspace.api.standalone
            ToolbarComponentsCustomizer ViewComponentCustomizer]
           ro.sync.exml.workspace.api.standalone.ui.ToolbarButton))

(defn -applicationStarted [this app-ws]
  (reset! workspace/instance app-ws)
  (mount/start)
  (.addViewComponentCustomizer
     app-ws
     (proxy [ViewComponentCustomizer] []
       (customizeView [viewInfo]
         (condp = (.getViewID viewInfo)
           workspace/results-view
           (doto viewInfo
              (.setTitle "ZDL/DWDS – Suchergebnisse")
              (.setIcon icon/gmd-result)
              (.setComponent results/output))
           viewInfo))))
  (.addToolbarComponentsCustomizer
   app-ws
   (proxy [ToolbarComponentsCustomizer] []
     (customizeToolbar [toolbarInfo]
       (condp = (.getToolbarID toolbarInfo)
         workspace/toolbar
         (let [article-search (ToolbarButton. search/action false)
               article-create (ToolbarButton. article/create false)
               article-delete (ToolbarButton. article/delete false)
               toolbar [icon/logo
                        article-search search/input
                        article-create article-delete]]
           (doto toolbarInfo
             (.setTitle "ZDL/DWDS")
             (.setComponents (into-array JComponent toolbar))))
         toolbarInfo)))))

(defn -applicationClosing [this]
  (mount/stop)
  (reset! workspace/instance nil)
  true)

