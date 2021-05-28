(ns zdl.lex.client.oxygen.extension
  (:gen-class
   :name de.zdl.oxygen.Extension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension])
  (:require [manifold.stream :as s]
            [mount.core :as mount]
            [zdl.lex.client.editors :as editors]
            [zdl.lex.client.icon :as icon]
            [zdl.lex.client.oxygen.editor-variables :as editor-variables]
            [zdl.lex.client.repl :as repl]
            [zdl.lex.client.status :as status]
            [zdl.lex.client.view.issue :as issue-view]
            [zdl.lex.client.view.results :as results-view]
            [zdl.lex.client.view.toolbar :as toolbar]
            [zdl.lex.client.workspace :as ws])
  (:import javax.swing.JComponent
           [ro.sync.exml.workspace.api.standalone ToolbarComponentsCustomizer ViewComponentCustomizer]
           [ro.sync.exml.workspace.api.util EditorVariableDescription EditorVariablesResolver]))

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

           (ws/views :issue)
           (doto viewInfo
             (.setTitle "ZDL/DWDS – Mantis-Tickets")
             (.setIcon icon/gmd-bug-report)
             (.setComponent issue-view/panel))

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
         toolbarInfo))))
  (.. app-ws
      (getUtilAccess)
      (addCustomEditorVariablesResolver
       (proxy [EditorVariablesResolver] []
         (getCustomResolverEditorVariableDescriptions
           []
           (->> editor-variables/descriptions
                (map #(EditorVariableDescription. (:name %) (:desc %)))
                (vec)))
         (resolveEditorVariables
           [s editor-url]
           (editor-variables/resolve-vars editor-url s)))))
  (status/trigger!))

(defn -applicationClosing [this]
  (mount/stop)
  true)

(comment
  repl/server
  editors/listeners)
