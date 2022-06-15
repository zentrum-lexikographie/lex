(ns zdl.lex.client.editors
  (:require
   [clojure.tools.logging :as log]
   [lambdaisland.uri :as uri]
   [seesaw.core :as ui]
   [zdl.lex.article :as article]
   [zdl.lex.bus :as bus]
   [zdl.lex.url :as lexurl])
  (:import
   (java.net URL)
   (ro.sync.exml.workspace.api PluginWorkspace)
   (ro.sync.exml.workspace.api.listeners WSEditorChangeListener WSEditorListener)
   (ro.sync.exml.workspace.api.standalone StandalonePluginWorkspace)))

(def editors
  (atom {}))

(def editing-area
  PluginWorkspace/MAIN_EDITING_AREA)

(defn read-editor-content
  [^StandalonePluginWorkspace workspace url]
  (try
    (let [url'          (URL. (str url))
          editor-access (.getEditorAccess workspace url' editing-area)]
      (with-open [is (.createContentInputStream editor-access)]
        (article/read-xml is)))
    (catch Throwable t
      (log/debug t url))))

(defn read-editor-contents
  [^StandalonePluginWorkspace workspace]
  (into
   {}
   (comp
    (map (fn [url] [url (read-editor-content workspace url)]))
    (remove (comp nil? second)))
   (keys @editors)))

(defn editor-listener
  "An editor listener for a given resource location"
  [workspace url]
  (proxy [WSEditorListener] []
    (documentTypeExtensionsReconfigured [])
    (editorPageAboutToBeChangedVeto [_] true)
    (editorPageChanged [])
    (editorAboutToBeClosedVeto [_] true)
    (editorAboutToBeSavedVeto [_] true)
    (editorSaved [_]
      (when-let [doc (read-editor-content workspace url)]
        (bus/publish! #{:editor-saved} {:url url :doc doc})))))

(defn add-editor-listener!
  [^StandalonePluginWorkspace workspace url]
  (when (lexurl/lex? url)
    (let [listener (editor-listener workspace url)]
      (ui/invoke-now
       (.. workspace
           (getEditorAccess (URL. (str url)) editing-area)
           (addEditorListener listener))
       (swap! editors assoc url listener)
       (bus/publish! #{:editor-added} {:url url})))))

(defn remove-editor-listener!
  [^StandalonePluginWorkspace workspace url]
  (when (lexurl/lex? url)
    (ui/invoke-now
     (.. workspace
         (getEditorAccess (URL. (str url)) editing-area)
         (removeEditorListener (@editors url)))
     (swap! editors dissoc url)
     (bus/publish! #{:editor-removed} {:url url}))))

(defn remove-all-editor-listeners!
  [^StandalonePluginWorkspace workspace]
  (doseq [[url listener] @editors]
    (.. workspace
        (getEditorAccess (URL. (str url)) editing-area)
        (removeEditorListener listener)))
  (reset! editors {}))

(defn editor-change-listener
  [^StandalonePluginWorkspace workspace]
  (proxy [WSEditorChangeListener] []
    (editorAboutToBeOpened [_])
    (editorAboutToBeOpenedVeto [_] true)
    (editorOpened [url]
      (let [url (uri/uri url)]
        (add-editor-listener! workspace url)
        (when (lexurl/lex? url)
          (when-let [doc (read-editor-content workspace url)]
            (bus/publish! #{:editor-opened} {:url url :doc doc})))))
    (editorPageChanged [_])
    (editorRelocated [from to]
      (remove-editor-listener! workspace from)
      (add-editor-listener! workspace to))
    (editorAboutToBeClosed [url]
      (let [url (uri/uri url)]
        (remove-editor-listener! workspace url) true))
    (editorsAboutToBeClosed [urls]
      (doseq [url urls]
        (let [url (uri/uri url)]
          (remove-editor-listener! workspace url)))
      true)
    (editorClosed [url]
      (let [url (uri/uri url)]
        (when (lexurl/lex? url)
          (bus/publish! #{:editor-closed} {:url url}))))
    (editorActivated [url]
      (let [url (uri/uri url)]
        (when (lexurl/lex? url)
          (when-let [doc (read-editor-content workspace url)]
            (bus/publish! #{:editor-activated} {:url url :doc doc})))))
    (editorSelected [_])
    (editorDeactivated [url]
      (let [url (uri/uri url)]
        (when (lexurl/lex? url)
          (bus/publish! #{:editor-deactivated} {:url url}))))))

(defn bind-editor-listeners!
  [^StandalonePluginWorkspace workspace]
  (let [editor-change-listener (editor-change-listener workspace)]
    (ui/invoke-now
     (remove-all-editor-listeners! workspace)
     (.addEditorChangeListener workspace editor-change-listener editing-area))
    (fn []
      (ui/invoke-now
       (.removeEditorChangeListener workspace editor-change-listener editing-area)
       (remove-all-editor-listeners! workspace)))))
