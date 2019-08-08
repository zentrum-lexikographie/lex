(ns zdl-lex-client.editors
  (:require [mount.core :refer [defstate]]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.workspace :as workspace]
            [manifold.stream :as s]
            [taoensso.timbre :as timbre])
  (:import java.net.URL
           [ro.sync.exml.workspace.api.listeners WSEditorChangeListener WSEditorListener]
           ro.sync.exml.workspace.api.PluginWorkspace
           ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace))

(def ^:private editing-area PluginWorkspace/MAIN_EDITING_AREA)

(defn xml-str [^URL url]
  (let [^StandalonePluginWorkspace ws workspace/instance]
    (with-open [xml (.. ws
                        (getEditorAccess url editing-area)
                        (createContentReader))]
      (slurp xml))))

(defn editor-listener [url]
  "An editor listener for a given resource location"
  (proxy [WSEditorListener] []
    (documentTypeExtensionsReconfigured [])
    (editorPageAboutToBeChangedVeto [_] true)
    (editorPageChanged [])
    (editorAboutToBeClosedVeto [_] true)
    (editorAboutToBeSavedVeto [_] true)
    (editorSaved [_]
      (bus/publish! :editor-saved url))))

(defn editors-listener [^StandalonePluginWorkspace ws editors]
  "A listener administering editor listeners"
  (let [add! (fn [url]
               (when (article/webdav? (str url))
                 (let [listener (editor-listener url)]
                   (.. ws
                       (getEditorAccess url editing-area)
                       (addEditorListener listener))
                   (swap! editors assoc url listener))))
        remove! (fn [url]
                  (when (article/webdav? (str url))
                    (.. ws
                        (getEditorAccess url editing-area)
                        (removeEditorListener (@editors url)))
                    (swap! editors dissoc url)
                    (bus/publish! :editor-active [url false])))]
    (proxy [WSEditorChangeListener] []
      (editorAboutToBeOpened [_])
      (editorAboutToBeOpenedVeto [_] true)
      (editorAboutToBeClosed [url]
        (remove! url) true)
      (editorsAboutToBeClosed [urls]
        (doseq [url urls] (remove! url)) true)
      (editorPageChanged [_])
      (editorActivated [url]
        (when (article/webdav? (str url))
          (bus/publish! :editor-active [url true])))
      (editorSelected [_])
      (editorDeactivated [url]
        (when (article/webdav? (str url))
          (bus/publish! :editor-active [url false])))
      (editorClosed [_])
      (editorOpened [url]
        (add! url))
      (editorRelocated [from to]
        (remove! from)
        (add! to)))))

(defstate listeners
  :start (let [^StandalonePluginWorkspace ws workspace/instance
               editors (atom {})
               listener (editors-listener ws editors)]
           (.. ws (addEditorChangeListener listener editing-area))
           {:editors editors :listener listener})
  :stop (let [^StandalonePluginWorkspace ws workspace/instance
              {:keys [editors listener]} listeners]
          (doseq [[url listener] @editors]
            (.. ws
                (getEditorAccess url editing-area)
                (removeEditorListener listener)))
          (reset! editors {})
          (.. ws (removeEditorChangeListener listener editing-area))))

(defstate activation-logger
  :start (let [subscription (bus/subscribe :editor-active)]
           (s/consume #(timbre/info %) subscription)
           subscription)
  :stop (activation-logger))
