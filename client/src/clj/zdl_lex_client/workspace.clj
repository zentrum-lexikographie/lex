(ns zdl-lex-client.workspace
  (:require [cemerick.url :refer [url]]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.http :as http]
            [zdl-lex-common.xml :as xml])
  (:import java.net.URL
           org.w3c.dom.Document
           ro.sync.exml.workspace.api.PluginWorkspace
           ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace))

(def views
  {:results "zdl-lex-results-view"
   :toolbar "zdl-lex-client-toolbar"
   :article "zdl-lex-article-view"})

(defprotocol Workspace
  (open-article
    [this id]
    "Opens an article in an editor.")
  (show-view
    [this id]
    [this id request-focus?]
    "Opens/shows a workspace view.")
  (xml-document
    [this ^URL url]
    "Parses the editor content associated with the given URL into a DOM.")
  (add-editor-change-listener [this listener])
  (remove-editor-change-listener [this listener])
  (add-editor-listener [this url listener])
  (remove-editor-listener [this url listener]))

(def ^:private editing-area PluginWorkspace/MAIN_EDITING_AREA)

(extend-protocol Workspace
  StandalonePluginWorkspace
  (show-view
    ([this id] (show-view this id true))
    ([^StandalonePluginWorkspace this id request-focus?]
     (.. this (showView (views id) request-focus?))))
  (open-article
    [^StandalonePluginWorkspace this id]
    (.. this (open (http/id->url id))))
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
    (.. this (getEditorAccess url editing-area) (removeEditorListener listener)))
  (xml-document [^StandalonePluginWorkspace this ^URL url]
    (with-open [editor-reader (.. this
                                  (getEditorAccess url editing-area)
                                  (createContentInputStream))]
      (xml/parse editor-reader))))

(defstate instance
  :start
  (reify Workspace
    (open-article [_ id]
      (bus/publish! :editor-active [(http/id->url id) true])
      true)
    (show-view [_ id] (show-view _ id true))
    (show-view [_ id request-focus?]
      (timbre/info {:id id :request-focus? request-focus?}))
    (add-editor-change-listener [_ _])
    (remove-editor-change-listener [_ _])
    (add-editor-listener [_ _ _])
    (remove-editor-listener [_ _ _])
    (xml-document [_ url]
      (timbre/info url)
      (http/get-xml url))))
