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

(def editors (atom {}))

(defn- editor-listener [url]
  "An editor listener for a given resource location"
  (proxy [WSEditorListener] []
    (documentTypeExtensionsReconfigured [])
    (editorPageAboutToBeChangedVeto [_] true)
    (editorPageChanged [])
    (editorAboutToBeClosedVeto [_] true)
    (editorAboutToBeSavedVeto [_] true)
    (editorSaved [_]
      (bus/publish! :editor-saved url))))

(defn- add-listener [url]
  (when (article/webdav? (str url))
    (let [listener (editor-listener url)]
      (workspace/add-editor-listener workspace/instance url listener)
      (swap! editors assoc url listener))))

(defn- remove-listener [url]
  (when (article/webdav? (str url))
    (workspace/remove-editor-listener workspace/instance url (@editors url))
    (swap! editors dissoc url)
    (bus/publish! :editor-active [url false])))

(defn- remove-all-listeners []
  (doseq [[url listener] @editors]
    (workspace/remove-editor-listener workspace/instance url listener))
  (reset! editors {}))

(def editor-change-listener
  (proxy [WSEditorChangeListener] []
    (editorAboutToBeOpened [_])
    (editorAboutToBeOpenedVeto [_] true)
    (editorAboutToBeClosed [url]
      (remove-listener url) true)
    (editorsAboutToBeClosed [urls]
      (doseq [url urls] (remove-listener url)) true)
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
      (add-listener url))
    (editorRelocated [from to]
      (remove-listener from)
      (add-listener to))))

(defstate listeners
  :start (do
           (remove-all-listeners)
           (workspace/add-editor-change-listener workspace/instance
                                                 editor-change-listener))
  :stop (do
          (remove-all-listeners)
          (workspace/remove-editor-change-listener workspace/instance
                                                   editor-change-listener)))

(defstate activation-logger
  :start (let [subscription (bus/subscribe :editor-active)]
           (s/consume #(timbre/info %) subscription)
           subscription)
  :stop (s/close! activation-logger))
