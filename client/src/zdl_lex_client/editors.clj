(ns zdl-lex-client.editors
  (:require [mount.core :refer [defstate]]
            [clojure.tools.logging :as log]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.workspace :as ws]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.url :as lexurl])
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
      (bus/publish! :editor-saved [url true]))))

(defn- add-listener [url]
  (when (lexurl/lex? url)
    (let [listener (editor-listener url)]
      (ws/add-editor-listener ws/instance url listener)
      (swap! editors assoc url listener))))

(defn- remove-listener [url]
  (when (lexurl/lex? url)
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
      (when (lexurl/lex? url)
        (bus/publish! :editor-active [url true])))
    (editorSelected [_])
    (editorDeactivated [url]
      (when (lexurl/lex? url)
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

(defn- editor-event->excerpt [[url active?]]
  (when active?
    (try
      (some->> (ws/xml-document ws/instance url)
               (article/doc->articles)
               (map article/excerpt)
               (first)
               (merge {:url url})
               (bus/publish! :article))
      (catch Exception e (log/warn e)))))

(defstate editor->article
  :start [(bus/listen :editor-active editor-event->excerpt)
          (bus/listen :editor-saved editor-event->excerpt)]
  :stop (doseq [s editor->article] (s)))

