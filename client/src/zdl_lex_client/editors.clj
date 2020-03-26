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
      (bus/publish! [:editor-saved] url))))

(defn- add-editor [url]
  (when (lexurl/lex? url)
    (let [listener (editor-listener url)]
      (ws/add-editor-listener ws/instance url listener)
      (swap! editors assoc url listener)
      (bus/publish! [:editor-added] url))))

(defn- remove-editor [url]
  (when (lexurl/lex? url)
    (ws/remove-editor-listener ws/instance url (@editors url))
    (swap! editors dissoc url)
    (bus/publish! [:editor-removed] url)))

(defn- remove-all-editors []
  (doseq [[url listener] @editors]
    (ws/remove-editor-listener ws/instance url listener))
  (reset! editors {}))

(def editor-change-listener
  (proxy [WSEditorChangeListener] []
    (editorAboutToBeOpened [_])
    (editorAboutToBeOpenedVeto [_] true)
    (editorOpened [url]
      (add-editor url)
      (when (lexurl/lex? url)
        (bus/publish! [:editor-opened] url)))
    (editorPageChanged [_])
    (editorRelocated [from to]
      (remove-editor from)
      (add-editor to))
    (editorAboutToBeClosed [url]
      (remove-editor url) true)
    (editorsAboutToBeClosed [urls]
      (doseq [url urls] (remove-editor url)) true)
    (editorClosed [url]
      (when (lexurl/lex? url)
        (bus/publish! [:editor-closed] url)))
    (editorActivated [url]
      (when (lexurl/lex? url)
        (bus/publish! [:editor-activated] url)))
    (editorSelected [_])
    (editorDeactivated [url]
      (when (lexurl/lex? url)
        (bus/publish! [:editor-deactivated] url)))))

(defstate listeners
  :start (do
           (remove-all-editors)
           (ws/add-editor-change-listener ws/instance editor-change-listener))
  :stop (do
          (ws/remove-editor-change-listener ws/instance editor-change-listener)
          (remove-all-editors)))

(defn- editor-changed
  [_ url]
  (try
    (if-let [doc (ws/xml-document ws/instance url)]
      (let [articles (article/doc->articles doc)
            errors (seq (mapcat article/check-typography articles))
            base-data (merge {:url url} (when errors {:errors errors}))]
        (doseq [article articles]
          (some->> (article/excerpt article) (merge base-data)
           (bus/publish! [:article])))))
    (catch Throwable t (log/warn "" t))))

(defstate editor-changes
  :start (bus/listen [:editor-activated :editor-saved] editor-changed)
  :stop (editor-changes))

