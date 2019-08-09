(ns zdl-lex-client.editors
  (:require [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.workspace :as ws])
  (:import [ro.sync.exml.workspace.api.listeners WSEditorChangeListener WSEditorListener]))

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
  (when (http/webdav? url)
    (let [listener (editor-listener url)]
      (ws/add-editor-listener ws/instance url listener)
      (swap! editors assoc url listener))))

(defn- remove-listener [url]
  (when (http/webdav? url)
    (ws/remove-editor-listener ws/instance url (@editors url))
    (swap! editors dissoc url)
    (bus/publish! :editor-active [url false])))

(defn- remove-all-listeners []
  (doseq [[url listener] @editors]
    (ws/remove-editor-listener ws/instance url listener))
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
      (when (http/webdav? url)
        (bus/publish! :editor-active [url true])))
    (editorSelected [_])
    (editorDeactivated [url]
      (when (http/webdav? url)
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
           (ws/add-editor-change-listener ws/instance editor-change-listener))
  :stop (do
          (ws/remove-editor-change-listener ws/instance editor-change-listener)
          (remove-all-listeners)))

(defstate activation-logger
  :start (let [subscription (bus/subscribe :editor-active)]
           (s/consume #(timbre/info %) subscription)
           subscription)
  :stop (s/close! activation-logger))
