(ns zdl.lex.oxygen.extension
  (:gen-class
   :name de.zdl.oxygen.Extension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension])
  (:require
   [clojure.string :as str]
   [integrant.core :as ig]
   [taoensso.telemere :as tm]
   [zdl.lex.client :as client]
   [zdl.lex.oxygen.workspace :as workspace]
   [zdl.lex.ui.issue :as issue]
   [zdl.lex.ui.links :as links]
   [zdl.lex.ui.search :as search]
   [zdl.lex.ui.toolbar :as toolbar]
   [zdl.lex.ui.util :as util])
  (:import
   (javax.swing JComponent)
   (ro.sync.exml.workspace.api.standalone ToolbarComponentsCustomizer ViewComponentCustomizer)
   (ro.sync.exml.workspace.api.util EditorVariableDescription EditorVariablesResolver)))

(def system
  (atom nil))

(defn -applicationStarted
  [_ workspace]
  (try
    (alter-var-root #'workspace/instance (constantly workspace))
    (swap! client/id assoc :oxygen-version (.getVersion workspace))
    (reset! system (ig/init client/config))
    (.addViewComponentCustomizer
     workspace
     (proxy [ViewComponentCustomizer] []
       (customizeView [viewInfo]
         (condp = (.getViewID viewInfo)
           (workspace/views :results)
           (doto viewInfo
             (.setTitle "ZDL/DWDS – Suchergebnisse")
             (.setIcon (util/icon :list))
             (.setComponent search/panel))

           (workspace/views :issue)
           (doto viewInfo
             (.setTitle "ZDL/DWDS – Mantis-Tickets")
             (.setIcon (util/icon :bug-report))
             (.setComponent issue/panel))

           (workspace/views :links)
           (doto viewInfo
             (.setTitle "ZDL/DWDS – Verweise")
             (.setIcon (util/icon :compare-arrows))
             (.setComponent links/pane))
           viewInfo))))
    (.addToolbarComponentsCustomizer
     workspace
     (proxy [ToolbarComponentsCustomizer] []
       (customizeToolbar [toolbarInfo]
         (condp = (.getToolbarID toolbarInfo)
           (workspace/views :toolbar)
           (doto toolbarInfo
             (.setTitle "ZDL/DWDS")
             (.setComponents (into-array JComponent toolbar/components)))
           toolbarInfo))))
    (..
     workspace
     (getUtilAccess)
     (addCustomEditorVariablesResolver
      (proxy [EditorVariablesResolver] []
        (getCustomResolverEditorVariableDescriptions
          []
          [(EditorVariableDescription.
            "${zdl.user}"
            "Anmeldename des aktuellen ZDL-Lex-Benutzers")])
        (resolveEditorVariables
          [s editor-url]
          (when s
            (let [user (some-> client/active-user deref)
                  user (or user (System/getProperty "user.name") "")]
              (str/replace s #"\$\{zdl\.user\}" user)))))))
    (workspace/bind-editor-change-listener)
    (catch Throwable t (tm/error! t)))
  nil)

(defn -applicationClosing
  [_]
  (try
    (ig/halt! @system)
    (reset! system nil)
    (workspace/unbind-editor-change-listener)
    (alter-var-root #'workspace/instance (constantly nil))
    (catch Throwable t (tm/error! t)))
  true)
