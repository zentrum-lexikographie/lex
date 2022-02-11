(ns zdl.lex.client.oxygen
  (:gen-class
   :name de.zdl.oxygen.Extension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension])
  (:require [clojure.java.browse :refer [browse-url]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [lambdaisland.uri :as uri]
            [mount.core :as mount :refer [defstate]]
            [nrepl.server :as repl]
            [seesaw.core :as ui]
            [zdl.lex.article.xml :as axml]
            [zdl.lex.bus :as bus]
            [zdl.lex.client.http :as client.http]
            [zdl.lex.client.icon :as client.icon]
            [zdl.lex.client.issue :as client.issue]
            [zdl.lex.client.results :as client.results]
            [zdl.lex.client.toolbar :as client.toolbar]
            [zdl.lex.client.validation :as client.validation]
            [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :as fs]
            [zdl.lex.url :as lexurl]
            [clojure.core.async :as a]
            [zdl.lex.client.search :as client.search]
            [zdl.lex.client.links :as client.links])
  (:import java.net.URL
           javax.swing.JComponent
           [ro.sync.exml.workspace.api.listeners WSEditorChangeListener WSEditorListener]
           ro.sync.exml.workspace.api.PluginWorkspace
           [ro.sync.exml.workspace.api.standalone StandalonePluginWorkspace ToolbarComponentsCustomizer ViewComponentCustomizer]
           [ro.sync.exml.workspace.api.util EditorVariableDescription EditorVariablesResolver]))

;; # REPL

(defstate repl-server
  :start (when-let [port (getenv "REPL_PORT")]
           (log/info (format "Starting REPL server @%s/tcp" port))
           (repl/start-server :port (Integer/parseInt port)))
  :stop (some-> repl-server repl/stop-server))

;; # Workspace

(defprotocol Workspace
  (preferences-dir
    [this]
    "A directory where user data can be stored")
  (open-url
    [this url]
    "Opens URL in a browser.")
  (open-article
    [this id]
    "Opens an article in an editor.")
  (preview-article
    [this]
    "Opens a preview of the article in the current editor.")
  (show-view
    [this id]
    [this id request-focus?]
    "Opens/shows a workspace view.")
  (editor-url
    [this]
    "URL of the current editor.")
  (editor-urls
    [this]
    "URLs of all currently opened editors.")
  (xml-document
    [this url]
    "Parses the editor content associated with the given URL into a DOM.")
  (modified?
    [this url]
    "Checks whether the editor' content associated with the given URL is modified.")
  (add-editor-change-listener [this listener])
  (remove-editor-change-listener [this listener])
  (add-editor-listener [this url listener])
  (remove-editor-listener [this url listener]))

(def editing-area
  PluginWorkspace/MAIN_EDITING_AREA)

(defn get-editor-access
  [^StandalonePluginWorkspace ws url]
  (.getEditorAccess ws url editing-area))

(def views
  {:results "zdl-lex-results-view"
   :toolbar "zdl-lex-client-toolbar"
   :issue   "zdl-lex-issue-view"
   :links   "zdl-lex-links-view"})

(def preview-url
  (uri/uri "https://zwei.dwds.de/wb/existdb/"))

(extend-protocol Workspace
  StandalonePluginWorkspace
  (preferences-dir
    [^StandalonePluginWorkspace this]
    (io/file (.getPreferencesDirectory this)))
  (show-view
    ([this id] (show-view this id true))
    ([^StandalonePluginWorkspace this id request-focus?]
     (.. this (showView (views id) (or request-focus? false)))))
  (open-url
    [^StandalonePluginWorkspace this url]
    (a/thread
      (.openInExternalApplication this url false "text/html")))
  (open-article
    [^StandalonePluginWorkspace this url]
    (a/thread
      (.open this url)))
  (preview-article
    [^StandalonePluginWorkspace this]
    (let [url (editor-url this)
          id  (lexurl/url->id (uri/uri url))]
      (when id
        (if (.. (get-editor-access this url) (isModified))
          (->>
           ["Der aktuelle Artikel ist nicht gespeichert."
            "Bitte speichern Sie ihre Arbeit, um eine aktuelle Vorschau zu erhalten."]
           (str/join \newline)
           (ui/dialog :parent (.getParentFrame this)
                      :modal? true
                      :type :error
                      :content)
           (ui/pack!)
           (ui/show!)
           (ui/invoke-later))
          (open-url this (URL. (str (uri/assoc-query preview-url :d id))))))))
  (add-editor-change-listener
    [^StandalonePluginWorkspace this listener]
    (.. this (addEditorChangeListener listener editing-area)))
  (remove-editor-change-listener
    [^StandalonePluginWorkspace this listener]
    (.. this (removeEditorChangeListener listener editing-area)))
  (add-editor-listener
    [^StandalonePluginWorkspace this url listener]
    (.. this (getEditorAccess url editing-area) (addEditorListener listener)))
  (remove-editor-listener
    [^StandalonePluginWorkspace this url listener]
    (.. (get-editor-access this url) (removeEditorListener listener)))
  (modified? [^StandalonePluginWorkspace this url]
    (.. (get-editor-access this url) (isModified)))
  (editor-url [^StandalonePluginWorkspace this]
    (.. this (getCurrentEditorAccess editing-area) (getEditorLocation)))
  (editor-urls [^StandalonePluginWorkspace this]
    (.getAllEditorLocations this editing-area))
  (xml-document [^StandalonePluginWorkspace this url]
    (try
      (with-open [is (.. (get-editor-access this url) (createContentInputStream))]
        (axml/read-xml is))
      (catch Throwable t
        (log/debug t url)))))

(def workspace-stub
  (let [editor-url (atom nil)]
    (reify Workspace
      (preferences-dir [_]
        (io/file (System/getProperty "user.dir") "tmp" "ws-prefs"))
      (open-url [_ url]
        (a/thread
          (browse-url url)))
      (open-article [_ url]
        (reset! editor-url url)
        (let [message {:url (uri/uri url)}]
          (bus/publish! #{:editor-opened} message)
          (bus/publish! #{:editor-activated} message))
        true)
      (preview-article [this]
        (when-let [url @editor-url]
          (let [id (lexurl/url->id (uri/uri url))]
            (open-url this (URL. (str (uri/assoc-query preview-url :d id)))))))
      (show-view [_ id] (show-view _ id true))
      (show-view [_ id request-focus?]
        (log/info {:id id :request-focus? request-focus?}))
      (add-editor-change-listener [_ _])
      (remove-editor-change-listener [_ _])
      (add-editor-listener [_ _ _])
      (remove-editor-listener [_ _ _])
      (modified? [_ _] false)
      (editor-url [_]
        @editor-url)
      (editor-urls [_]
        (some->> @editor-url vector))
      (xml-document [_ url]
        (let [id       (lexurl/url->id (uri/uri url))
              request  {:url     (uri/join "article/" id)
                        :headers {"Accept" "application/xml"}
                        :as      :stream}
              response (client.http/request request)]
          (with-open [body (get response :body)]
            (axml/read-xml body)))))))

(defstate workspace
  :start workspace-stub)

(defstate workspace-events
  :start (bus/listen
          #{:open-url :open-article :preview-article :show-view}
          (fn [topic {:keys [id request-focus? url view]}]
            (condp = topic
              :open-url        (open-url workspace
                                         (URL. (str url)))
              :open-article    (open-article workspace
                                             (URL. (str (lexurl/id->url id))))
              :preview-article (preview-article workspace)
              :show-view       (show-view workspace
                                          view
                                          request-focus?))))
  :stop (workspace-events))

;; # Editors

(def editors
  (atom {}))

(defn editor-listener
  "An editor listener for a given resource location"
  [url]
  (proxy [WSEditorListener] []
    (documentTypeExtensionsReconfigured [])
    (editorPageAboutToBeChangedVeto [_] true)
    (editorPageChanged [])
    (editorAboutToBeClosedVeto [_] true)
    (editorAboutToBeSavedVeto [_] true)
    (editorSaved [_]
      (bus/publish! #{:editor-saved} {:url url}))))

(defn add-editor
  [url]
  (when (lexurl/lex? url)
    (let [listener (editor-listener url)]
      (ui/invoke-now
       (add-editor-listener workspace (URL. (str url)) listener)
       (swap! editors assoc url listener)
       (bus/publish! #{:editor-added} {:url url})))))

(defn remove-editor
  [url]
  (when (lexurl/lex? url)
    (ui/invoke-now
     (remove-editor-listener workspace (URL. (str url)) (@editors url))
     (swap! editors dissoc url)
     (bus/publish! #{:editor-removed} {:url url}))))

(def editor-change-listener
  (proxy [WSEditorChangeListener] []
    (editorAboutToBeOpened [_])
    (editorAboutToBeOpenedVeto [_] true)
    (editorOpened [url]
      (let [url (uri/uri url)]
        (add-editor url)
        (when (lexurl/lex? url)
          (bus/publish! #{:editor-opened} {:url url}))))
    (editorPageChanged [_])
    (editorRelocated [from to]
      (remove-editor from)
      (add-editor to))
    (editorAboutToBeClosed [url]
      (let [url (uri/uri url)]
        (remove-editor url) true))
    (editorsAboutToBeClosed [urls]
      (doseq [url urls]
        (let [url (uri/uri url)]
          (remove-editor url)))
      true)
    (editorClosed [url]
      (let [url (uri/uri url)]
        (when (lexurl/lex? url)
          (bus/publish! #{:editor-closed} {:url url}))))
    (editorActivated [url]
      (let [url' (uri/uri url)]
        (when (lexurl/lex? url')
          (when-let [doc (xml-document workspace url)]
            (bus/publish! #{:editor-content-changed} {:url url' :doc doc})))))
    (editorSelected [_])
    (editorDeactivated [url]
      (let [url (uri/uri url)]
        (when (lexurl/lex? url)
          (bus/publish! #{:editor-deactivated} {:url url}))))))

(defn remove-all-editors
  []
  (doseq [[url listener] @editors]
    (remove-editor-listener workspace (URL. (str url)) listener))
  (reset! editors {}))

(defstate editor-listeners
  :start (ui/invoke-now
           (remove-all-editors)
           (add-editor-change-listener workspace editor-change-listener))
  :stop (ui/invoke-now
          (remove-editor-change-listener workspace editor-change-listener)
          (remove-all-editors)))

;; # Editor content

(defn on-editor-content-change-event
  [_ {:keys [url] :as evt}]
  (let [url (URL. (str url))]
    (when-let [doc (xml-document workspace url)]
      (bus/publish! #{:editor-content-changed} (assoc evt :doc doc)))))

(defstate editor-content-changes
  :start (bus/listen #{:editor-opened :editor-saved} on-editor-content-change-event)
  :stop (editor-content-changes))

;; # Validation

(defn on-validation-event
  [manager topic {:keys [validate? url doc]}]
  (when (= topic :validate?)
    (client.validation/reset-results! manager)
    (when validate?
      (doseq [url (editor-urls workspace)]
        (when-let [doc (xml-document workspace url)]
          (client.validation/add-results! manager url doc)))))
  (when (#{:editor-closed} topic)
    (client.validation/clear-results! manager url))
  (when @client.toolbar/validation-active?
    (when (= topic :editor-content-changed)
      (let [url (URL. (str url))]
        (client.validation/add-results! manager url doc)))))

(def validation-events
  #{:validate? :editor-content-changed :editor-closed})

(defstate validation
  :start (when (instance? PluginWorkspace workspace)
           (let [manager (.getResultsManager ^PluginWorkspace workspace)]
             (bus/listen
              validation-events #(on-validation-event manager %1 %2))))
  :stop (when validation
          (validation)))

(defstate remove-chrome-profile
  :start (-> (fs/file (preferences-dir workspace) "chrome-profile")
             (fs/delete! true)))

(def states
  #{
    #'client.search/input->
    #'client.search/->input
    #'client.results/search-requests->results
    #'client.toolbar/status-label-text
    #'client.toolbar/validation-action-states
    #'client.issue/issue-update
    #'client.issue/issue-renderer
    #'client.links/link-update
    #'client.links/link-renderer
    #'repl-server
    #'remove-chrome-profile
    #'workspace
    #'workspace-events
    #'editor-listeners
    #'editor-content-changes
    #'validation})

;; # Extension

(defn -applicationStarted
  [_ ws]
  (try
    (-> (mount/only states)
        (mount/swap {#'workspace ws})
        (mount/start))
    (catch Throwable t
      (log/error t "Error starting app")))
  (.addViewComponentCustomizer
     ws
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

           viewInfo))))
  (.addToolbarComponentsCustomizer
   ws
   (proxy [ToolbarComponentsCustomizer] []
     (customizeToolbar [toolbarInfo]
       (condp = (.getToolbarID toolbarInfo)
         (views :toolbar)
         (doto toolbarInfo
           (.setTitle "ZDL/DWDS")
           (.setComponents (into-array JComponent client.toolbar/components)))
         toolbarInfo))))
  (.. ws
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

(defn -applicationClosing
  [_]
  (mount/stop (mount/only states))
  true)
