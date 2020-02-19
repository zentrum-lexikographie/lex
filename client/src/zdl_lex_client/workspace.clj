(ns zdl-lex-client.workspace
  (:require [clojure.java.browse :refer [browse-url]]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.http :as http]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.url :as lexurl]
            [zdl-xml.util :as xml]
            [me.raynes.fs :as fs])
  (:import java.net.URL
           ro.sync.exml.workspace.api.PluginWorkspace
           ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace))

(def views
  {:results "zdl-lex-results-view"
   :toolbar "zdl-lex-client-toolbar"
   :issue "zdl-lex-issue-view"})

(defprotocol Workspace
  (preferences-dir
    [this]
    "A directory where user data can be stored")
  (open-url
    [this ^URL url]
    "Opens URL in a browser.")
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
  (modified?
    [this ^URL url]
    "Checks whether the editor' content associated with the given URL is modified.")
  (add-editor-change-listener [this listener])
  (remove-editor-change-listener [this listener])
  (add-editor-listener [this url listener])
  (remove-editor-listener [this url listener]))

(def ^:private editing-area PluginWorkspace/MAIN_EDITING_AREA)

(extend-protocol Workspace
  StandalonePluginWorkspace
  (preferences-dir
    [^StandalonePluginWorkspace this]
    (-> (.. this (getPreferencesDirectory)) fs/file fs/absolute fs/normalized))
  (show-view
    ([this id] (show-view this id true))
    ([^StandalonePluginWorkspace this id request-focus?]
     (.. this (showView (views id) request-focus?))))
  (open-url
    [^StandalonePluginWorkspace this ^URL url]
    (future (.. this (openInExternalApplication url false "text/html"))))
  (open-article
    [^StandalonePluginWorkspace this id]
    (.. this (open (lexurl/id->url id))))
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
  (modified? [^StandalonePluginWorkspace this ^URL url]
    (.. this (getEditorAccess url editing-area) (isModified)))
  (xml-document [^StandalonePluginWorkspace this ^URL url]
    (with-open [editor-reader (.. this
                                  (getEditorAccess url editing-area)
                                  (createContentInputStream))]
      (xml/->dom editor-reader))))

(defstate instance
  :start
  (reify Workspace
    (preferences-dir [_]
      (fs/file (env :user-dir) "tmp" "ws-prefs"))
    (open-url [_ url]
      (future (browse-url url)))
    (open-article [_ id]
      (bus/publish! :editor-active [(lexurl/id->url id) true])
      true)
    (show-view [_ id] (show-view _ id true))
    (show-view [_ id request-focus?]
      (timbre/info {:id id :request-focus? request-focus?}))
    (add-editor-change-listener [_ _])
    (remove-editor-change-listener [_ _])
    (add-editor-listener [_ _ _])
    (remove-editor-listener [_ _ _])
    (modified? [_ _] false)
    (xml-document [_ url]
      (timbre/info url)
      @(http/get-xml (-> url lexurl/url->id http/id->store-url)))))
