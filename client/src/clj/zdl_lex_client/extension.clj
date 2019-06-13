(ns zdl-lex-client.extension
  (:gen-class
   :name de.zdl.oxygen.Extension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension])
  (:require [mount.core :as mount]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.editors :as editors]
            [zdl-lex-client.help :as help]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.repl :as repl]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.results :as results]
            [zdl-lex-client.status :as status])
  (:import javax.swing.JComponent
           [ro.sync.exml.workspace.api.standalone
            ToolbarComponentsCustomizer ViewComponentCustomizer]
           ro.sync.exml.workspace.api.standalone.ui.ToolbarButton))

(defn -applicationStarted [this app-ws]
  (mount/start-with {#'zdl-lex-client.workspace/instance app-ws})
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
               open-help (ToolbarButton. help/action false)
               status-label status/label
               toolbar [icon/logo
                        status/label
                        search/input
                        article-search
                        open-help
                        article-create]]
           (doto toolbarInfo
             (.setTitle "ZDL/DWDS")
             (.setComponents (into-array JComponent toolbar))))
         toolbarInfo)))))

(defn -applicationClosing [this]
  (mount/stop)
  true)

(comment repl/server
         editors/listeners)
