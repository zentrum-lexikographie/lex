(ns zdl.lex.client.workspace
  (:require [clojure.java.browse :refer [browse-url]]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [zdl.lex.client.bus :as bus]
            [zdl.lex.client.http :as http]
            [zdl.lex.url :as lexurl]
            [zdl.xml.util :as xml])
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
  (editor-url
    [this]
    "URL of the current editor.")
  (editor-urls
    [this]
    "URLs of all currently opened editors.")
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
    (future (.. this (open (lexurl/id->url id)))))
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
  (editor-url [^StandalonePluginWorkspace this]
    (.. this (getCurrentEditorAccess editing-area) (getEditorLocation)))
  (editor-urls [^StandalonePluginWorkspace this]
    (. this (getAllEditorLocations editing-area)))
  (xml-document [^StandalonePluginWorkspace this ^URL url]
    (try
      (with-open [editor-reader (.. this
                                    (getEditorAccess url editing-area)
                                    (createContentInputStream))]
        (xml/->xdm editor-reader))
      (catch Throwable t
        (log/debug (str url) t)))))

(defstate instance
  :start
  (let [editor-url (atom nil)]
    (reify Workspace
      (preferences-dir [_]
        (fs/file (System/getProperty "user.dir") "tmp" "ws-prefs"))
      (open-url [_ url]
        (future (browse-url url)))
      (open-article [_ id]
        (let [url (lexurl/id->url id)]
          (reset! editor-url url)
          (bus/publish! [:editor-activated] url)
          true))
      (show-view [_ id] (show-view _ id true))
      (show-view [_ id request-focus?]
        (log/info {:id id :request-focus? request-focus?}))
      (add-editor-change-listener [_ _])
      (remove-editor-change-listener [_ _])
      (add-editor-listener [_ _ _])
      (remove-editor-listener [_ _ _])
      (modified? [_ _] false)
      (editor-url [_]
        @editor-url)
      (editor-urls [_]
        (some->> @editor-url vector))
      (xml-document [_ url]
        (http/get-xml (-> url lexurl/url->id http/id->store-url))))))

(defn editor-xml-document
  []
  (some->> (editor-url instance) (xml-document instance)))
