(ns zdl.lex.client.oxygen
  (:gen-class
   :name de.zdl.oxygen.Extension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension])
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [lambdaisland.uri :as uri]
   [seesaw.core :as ui]
   [zdl.lex.bus :as bus]
   [zdl.lex.env :as env]
   [zdl.lex.client.editors :refer [bind-editor-listeners!]]
   [zdl.lex.client.http :as client.http]
   [zdl.lex.client.icon :as client.icon]
   [zdl.lex.client.issue :as client.issue]
   [zdl.lex.client.links :as client.links]
   [zdl.lex.client.results :as client.results]
   [zdl.lex.client.toolbar :as client.toolbar]
   [zdl.lex.client.validation :as client.validation :refer [bind-validation-events!]]
   [zdl.lex.url :as lexurl]
   [zdl.lex.util :refer [install-uncaught-exception-handler!]])
  (:import
   (java.net URL)
   (javax.swing JComponent)
   (ro.sync.exml.workspace.api PluginWorkspace)
   (ro.sync.exml.workspace.api.standalone StandalonePluginWorkspace ToolbarComponentsCustomizer ViewComponentCustomizer)
   (ro.sync.exml.workspace.api.util EditorVariableDescription EditorVariablesResolver)))

(def views
  {:results "zdl-lex-results-view"
   :toolbar "zdl-lex-client-toolbar"
   :issue   "zdl-lex-issue-view"
   :links   "zdl-lex-links-view"})

(defn add-view-components!
  [^StandalonePluginWorkspace workspace]
  (.addViewComponentCustomizer
   workspace
   (proxy [ViewComponentCustomizer] []
     (customizeView [viewInfo]
       (condp = (.getViewID viewInfo)
         (views :results)
         (doto viewInfo
           (.setTitle "ZDL/DWDS – Suchergebnisse")
           (.setIcon client.icon/gmd-result)
           (.setComponent client.results/pane))

         (views :issue)
         (doto viewInfo
           (.setTitle "ZDL/DWDS – Mantis-Tickets")
           (.setIcon client.icon/gmd-bug-report)
           (.setComponent client.issue/panel))

         (views :links)
         (doto viewInfo
           (.setTitle "ZDL/DWDS – Verweise")
           (.setIcon client.icon/gmd-link-bidi)
           (.setComponent client.links/pane))

         viewInfo)))))

(defn add-toolbar-components!
  [^StandalonePluginWorkspace workspace]
  (.addToolbarComponentsCustomizer
   workspace
   (proxy [ToolbarComponentsCustomizer] []
     (customizeToolbar [toolbarInfo]
       (condp = (.getToolbarID toolbarInfo)
         (views :toolbar)
         (doto toolbarInfo
           (.setTitle "ZDL/DWDS")
           (.setComponents (into-array JComponent client.toolbar/components)))
         toolbarInfo)))))

(defn add-custom-editor-var-resolver!
  [^StandalonePluginWorkspace workspace]
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
          (let [user (some-> client.http/*auth* deref first)
                user (or user (System/getProperty "user.name") "")]
            (str/replace s #"\$\{zdl\.user\}" user))))))))

(defn open-url
  [^StandalonePluginWorkspace workspace url]
  (.openInExternalApplication workspace (URL. (str url)) false "text/html"))

(defn bind-open-article!
  [^StandalonePluginWorkspace workspace]
  (bus/listen
   #{:open-article}
   (fn [_ {:keys [id]}]
     (.open workspace (URL. (str (lexurl/id->url id)))))))

(defn bind-open-url!
  [^StandalonePluginWorkspace workspace]
  (bus/listen #{:open-url} (fn [_ {:keys [url]}] (open-url workspace url))))

(def editing-area
  PluginWorkspace/MAIN_EDITING_AREA)

(def preview-url
  (uri/uri "https://zwei.dwds.de/wb/existdb/"))

(defn preview-article!
  [^StandalonePluginWorkspace workspace & _]
  (let [editor-access (.getCurrentEditorAccess workspace editing-area)]
    (when-let [url (.getEditorLocation editor-access)]
      (when-let [id (lexurl/url->id (uri/uri url))]
        (if (.isModified editor-access)
          (->>
           ["Der aktuelle Artikel ist nicht gespeichert."
            "Bitte speichern Sie ihre Arbeit, um eine aktuelle Vorschau zu erhalten."]
           (str/join \newline)
           (ui/dialog :parent (.getParentFrame workspace)
                      :modal? true
                      :type :error
                      :content)
           (ui/pack!)
           (ui/show!)
           (ui/invoke-later))
          (open-url workspace (uri/assoc-query preview-url :d id)))))))

(defn bind-preview-article!
  [^StandalonePluginWorkspace workspace]
  (bus/listen #{:preview-article} (partial preview-article! workspace)))

(defn bind-show-view!
  [^StandalonePluginWorkspace workspace]
  (bus/listen
   #{:show-view}
   (fn [_ {:keys [view request-focus?]}]
     (.showView workspace (views view) (or request-focus? false)))))

;; # Extension

(def workspace
  (atom nil))

(def system
  (atom nil))

(def callbacks
  (atom nil))

(defn -applicationStarted
  [_ ^StandalonePluginWorkspace workspace']
  (try
    (install-uncaught-exception-handler!)
    (log/info "Starting ZDL-Lex/Oxygen extension")
    (ig/load-namespaces env/config env/client-config-keys)
    (reset! workspace workspace')
    (reset! system (ig/init env/config env/client-config-keys))
    (reset! callbacks [(bind-open-article! workspace')
                       (bind-open-url! workspace')
                       (bind-preview-article! workspace')
                       (bind-show-view! workspace')
                       (bind-editor-listeners! workspace')
                       (bind-validation-events! workspace')])
    (add-view-components! workspace')
    (add-toolbar-components! workspace')
    (add-custom-editor-var-resolver! workspace')
    (catch Throwable t
      (log/error t "Error starting ZDL-Lex/Oxygen extension")))
  nil)

(defn -applicationClosing
  [_]
  (try
    (log/info "Stopping ZDL-Lex/Oxygen extension")
    (doseq [callback @callbacks] (callback))
    (reset! callbacks nil)
    (when-let [system' @system]
      (ig/halt! system')
      (reset! system nil))
    (reset! workspace nil)
    (catch Throwable t
      (log/error t "Error stopping ZDL-Lex/Oxygen extension")))
  true)
