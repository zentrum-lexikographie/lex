(ns zdl-lex-client.editors
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.article :as article])
  (:import [ro.sync.exml.workspace.api.listeners WSEditorChangeListener WSEditorListener]
           ro.sync.exml.workspace.api.PluginWorkspace
           ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace))

(defn sync-id [id]
  (http/post-edn #(merge % {:path "/articles/exist/sync-id" :query {"id" id}}) {}))

(defstate save-events
  :start (let [ch (async/chan)]
           (async/go-loop []
             (when-let [url (async/<! ch)]
               (timbre/infof "<save> %s" url)
               (when-let [id (article/url->id url)]
                 (timbre/infof "<sync> %s" id)
                 (async/<!
                  (async/thread
                    (sync-id id))))
               (recur)))
           ch)
  :stop (async/close! save-events))

(defn editor-listener [url]
  "An editor listener for a given resource location"
  (proxy [WSEditorListener] []
    (documentTypeExtensionsReconfigured [])
    (editorPageAboutToBeChangedVeto [_] true)
    (editorPageChanged [])
    (editorAboutToBeClosedVeto [_] true)
    (editorAboutToBeSavedVeto [_] true)
    (editorSaved [_] (async/>!! save-events url))))

(def ^:private editing-area PluginWorkspace/MAIN_EDITING_AREA)

(defn editors-listener [^StandalonePluginWorkspace ws editors]
  "A listener administering editor listeners"
  (let [add! (fn [url]
               (let [listener (editor-listener (str url))]
                     (.. ws
                         (getEditorAccess url editing-area)
                         (addEditorListener listener))
                     (swap! editors assoc url listener)))
        remove! (fn [url]
                  (.. ws
                      (getEditorAccess url editing-area)
                      (removeEditorListener (@editors url)))
                  (swap! editors dissoc url))]
    (proxy [WSEditorChangeListener] []
      (editorAboutToBeOpened [_])
      (editorAboutToBeOpenedVeto [_] true)
      (editorAboutToBeClosed [url]
        (remove! url) true)
      (editorsAboutToBeClosed [urls]
        (doseq [url urls] (remove! url)) true)
      (editorPageChanged [_])
      (editorActivated [_])
      (editorSelected [_])
      (editorDeactivated [_])
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
