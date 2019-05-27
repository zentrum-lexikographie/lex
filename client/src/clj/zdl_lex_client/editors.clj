(ns zdl-lex-client.editors
  (:require [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.workspace :as workspace])
  (:import ro.sync.exml.workspace.api.listeners.WSEditorListener
           ro.sync.exml.workspace.api.listeners.WSEditorChangeListener
           ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace
           ro.sync.exml.workspace.api.PluginWorkspace))

(def editing-area PluginWorkspace/MAIN_EDITING_AREA)

(defstate listeners
  :start (let [^StandalonePluginWorkspace ws workspace/instance
               editors (atom {})
               add-editor (fn [url]
                            (let [listener (proxy [WSEditorListener] []
                                             (documentTypeExtensionsReconfigured [])
                                             (editorPageAboutToBeChangedVeto [_] true)
                                             (editorPageChanged [])
                                             (editorAboutToBeClosedVeto [_] true)
                                             (editorAboutToBeSavedVeto [_] true)
                                             (editorSaved [_] (timbre/info url)))]
                              (.. ws
                                  (getEditorAccess url editing-area)
                                  (addEditorListener listener))
                              (swap! editors assoc url listener)))
               remove-editor (fn [url]
                               (.. ws
                                   (getEditorAccess url editing-area)
                                   (removeEditorListener (@editors url)))
                               (swap! editors dissoc url))
               editors-listener (proxy [WSEditorChangeListener] []
                                  (editorAboutToBeOpened [_])
                                  (editorAboutToBeOpenedVeto [_] true)
                                  (editorAboutToBeClosed [url]
                                    (remove-editor url)
                                    true)
                                  (editorsAboutToBeClosed [urls]
                                    (doseq [url urls]
                                      (remove-editor url))
                                    true)
                                  (editorPageChanged [_])
                                  (editorActivated [_])
                                  (editorSelected [_])
                                  (editorDeactivated [_])
                                  (editorClosed [_])
                                  (editorOpened [url]
                                    (add-editor url))
                                  (editorRelocated [from to]
                                    (remove-editor from)
                                    (add-editor to)))]
           (.. ^StandalonePluginWorkspace workspace/instance
               (addEditorChangeListener editors-listener editing-area))
           {:editors editors :listener editors-listener})
  :stop (let [^StandalonePluginWorkspace ws workspace/instance
              {:keys [editors listener]} listeners]
          (doseq [[url listener] @editors]
            (.. ws
                (getEditorAccess url editing-area)
                (removeEditorListener listener)))
          (reset! editors {})
          (.. ws (removeEditorChangeListener listener editing-area))))
