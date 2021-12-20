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
  (uri/uri "http://zwei.dwds.de/wb/existdb/"))

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
      (remove-editor url) true)
    (editorsAboutToBeClosed [urls]
      (doseq [url urls] (remove-editor url)) true)
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



;; 18:23:03.754 [AWT-EventQueue-0] ERROR ro.sync.ui.application.action.q - Action enabled, even if editor does not support it.
;; #error {
;;  :cause "Cannot invoke \"Object.getClass()\" because \"target\" is null"
;;  :via
;;  [{:type clojure.lang.ExceptionInfo
;;    :message "could not stop [#'zdl.lex.client.oxygen/editor-listeners] due to"
;;    :data {}
;;    :at [mount.core$down$fn__3440 invoke "core.cljc" 96]}
;;   {:type java.lang.NullPointerException
;;    :message "Cannot invoke \"Object.getClass()\" because \"target\" is null"
;;    :at [clojure.lang.Reflector invokeInstanceMethod "Reflector.java" 97]}]
;;  :trace
;;  [[clojure.lang.Reflector invokeInstanceMethod "Reflector.java" 97]
;;   [zdl.lex.client.oxygen$fn__23681 invokeStatic "oxygen.clj" 139]
;;   [zdl.lex.client.oxygen$fn__23681 invoke "oxygen.clj" 93]
;;   [zdl.lex.client.oxygen$fn__23538$G__23478__23547 invoke "oxygen.clj" 44]
;;   [zdl.lex.client.oxygen$remove_all_editors invokeStatic "oxygen.clj" 278]
;;   [zdl.lex.client.oxygen$remove_all_editors invoke "oxygen.clj" 275]
;;   [zdl.lex.client.oxygen$fn__23818$fn__23819 invoke "oxygen.clj" 287]
;;   [clojure.lang.AFn applyToHelper "AFn.java" 152]
;;   [clojure.lang.AFn applyTo "AFn.java" 144]
;;   [clojure.core$apply invokeStatic "core.clj" 667]
;;   [clojure.core$apply invoke "core.clj" 662]
;;   [seesaw.invoke$invoke_now_STAR_$invoker__5378 invoke "invoke.clj" 18]
;;   [seesaw.invoke$invoke_now_STAR_ invokeStatic "invoke.clj" 20]
;;   [seesaw.invoke$invoke_now_STAR_ doInvoke "invoke.clj" 16]
;;   [clojure.lang.RestFn invoke "RestFn.java" 410]
;;   [zdl.lex.client.oxygen$fn__23818 invokeStatic "oxygen.clj" 285]
;;   [zdl.lex.client.oxygen$fn__23818 invoke "oxygen.clj" 281]
;;   [mount.core$record_BANG_ invokeStatic "core.cljc" 74]
;;   [mount.core$record_BANG_ invoke "core.cljc" 73]
;;   [mount.core$down$fn__3440 invoke "core.cljc" 97]
;;   [mount.core$down invokeStatic "core.cljc" 96]
;;   [mount.core$down invoke "core.cljc" 86]
;;   [mount.core$bring invokeStatic "core.cljc" 247]
;;   [mount.core$bring invoke "core.cljc" 239]
;;   [mount.core$stop invokeStatic "core.cljc" 300]
;;   [mount.core$stop doInvoke "core.cljc" 291]
;;   [clojure.lang.RestFn applyTo "RestFn.java" 137]
;;   [clojure.core$apply invokeStatic "core.clj" 667]
;;   [clojure.core$apply invoke "core.clj" 662]
;;   [mount.core$stop invokeStatic "core.cljc" 295]
;;   [mount.core$stop doInvoke "core.cljc" 291]
;;   [clojure.lang.RestFn invoke "RestFn.java" 408]
;;   [zdl.lex.client.oxygen$_applicationClosing invokeStatic "oxygen.clj" 407]
;;   [zdl.lex.client.oxygen$_applicationClosing invoke "oxygen.clj" 405]
;;   [de.zdl.oxygen.Extension applicationClosing nil -1]
;;   [ro.sync.exml.z$2 applicationAboutToBeClosedOrHidden nil -1]
;;   [ro.sync.exml.MainFrame ghu nil -1]
;;   [ro.sync.exml.MainFrame mhu nil -1]
;;   [ro.sync.exml.MainFrame qgu nil -1]
;;   [ro.sync.exml.MainFrame xcf nil -1]
;;   [ro.sync.exml.MainFrame handleQuit nil -1]
;;   [ro.sync.exml.MainFrame$43 lbk nil -1]
;;   [ro.sync.ui.application.action.r actionPerformed nil -1]
;;   [javax.swing.AbstractButton fireActionPerformed "AbstractButton.java" 1972]
;;   [javax.swing.AbstractButton$Handler actionPerformed "AbstractButton.java" 2313]
;;   [javax.swing.DefaultButtonModel fireActionPerformed "DefaultButtonModel.java" 405]
;;   [javax.swing.DefaultButtonModel setPressed "DefaultButtonModel.java" 262]
;;   [javax.swing.AbstractButton doClick "AbstractButton.java" 374]
;;   [javax.swing.plaf.basic.BasicMenuItemUI doClick "BasicMenuItemUI.java" 1028]
;;   [javax.swing.plaf.basic.BasicMenuItemUI$Handler mouseReleased "BasicMenuItemUI.java" 1072]
;;   [java.awt.Component processMouseEvent "Component.java" 6626]
;;   [javax.swing.JComponent processMouseEvent "JComponent.java" 3389]
;;   [java.awt.Component processEvent "Component.java" 6391]
;;   [java.awt.Container processEvent "Container.java" 2266]
;;   [java.awt.Component dispatchEventImpl "Component.java" 5001]
;;   [java.awt.Container dispatchEventImpl "Container.java" 2324]
;;   [java.awt.Component dispatchEvent "Component.java" 4833]
;;   [java.awt.LightweightDispatcher retargetMouseEvent "Container.java" 4948]
;;   [java.awt.LightweightDispatcher processMouseEvent "Container.java" 4575]
;;   [java.awt.LightweightDispatcher dispatchEvent "Container.java" 4516]
;;   [java.awt.Container dispatchEventImpl "Container.java" 2310]
;;   [java.awt.Window dispatchEventImpl "Window.java" 2780]
;;   [java.awt.Component dispatchEvent "Component.java" 4833]
;;   [java.awt.EventQueue dispatchEventImpl "EventQueue.java" 773]
;;   [java.awt.EventQueue$4 run "EventQueue.java" 722]
;;   [java.awt.EventQueue$4 run "EventQueue.java" 716]
;;   [java.security.AccessController doPrivileged "AccessController.java" 399]
;;   [java.security.ProtectionDomain$JavaSecurityAccessImpl doIntersectionPrivilege "ProtectionDomain.java" 86]
;;   [java.security.ProtectionDomain$JavaSecurityAccessImpl doIntersectionPrivilege "ProtectionDomain.java" 97]
;;   [java.awt.EventQueue$5 run "EventQueue.java" 746]
;;   [java.awt.EventQueue$5 run "EventQueue.java" 744]
;;   [java.security.AccessController doPrivileged "AccessController.java" 399]
;;   [java.security.ProtectionDomain$JavaSecurityAccessImpl doIntersectionPrivilege "ProtectionDomain.java" 86]
;;   [java.awt.EventQueue dispatchEvent "EventQueue.java" 743]
;;   [java.awt.EventDispatchThread pumpOneEventForFilters "EventDispatchThread.java" 203]
;;   [java.awt.EventDispatchThread pumpEventsForFilter "EventDispatchThread.java" 124]
;;   [java.awt.EventDispatchThread pumpEventsForHierarchy "EventDispatchThread.java" 113]
;;   [java.awt.EventDispatchThread pumpEvents "EventDispatchThread.java" 109]
;;   [java.awt.EventDispatchThread pumpEvents "EventDispatchThread.java" 101]
;;   [java.awt.EventDispatchThread run "EventDispatchThread.java" 90]]}
