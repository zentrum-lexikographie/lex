(ns zdl-lex-client.view-extension
  (:require [zdl-lex-client.metasearch :as metasearch]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.url :as url]
            [seesaw.core :as ui]
            [zdl-lex-client.icon :as icon]
            [clojure.core.async :as async])
  (:import [java.net URL]
           [javax.swing JComponent]
           [ro.sync.exml.workspace.api.standalone
            ViewComponentCustomizer ToolbarComponentsCustomizer]
           [ro.sync.exml.workspace.api.standalone.ui ToolbarButton])
  (:gen-class
   :name de.zdl.oxygen.ViewExtension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension]))

(defonce ws (atom nil))

(defn open [url]
  (some-> @ws (.open url)))

(defn -applicationStarted [this app-ws]
  (reset! ws app-ws)
  (let [metasearch (metasearch/form)

        article-search (ToolbarButton. search/action false)
        article-create (ToolbarButton. article/create false)
        article-delete (ToolbarButton. article/delete false)

        toolbar [icon/logo article-search search/input article-create article-delete]]

    (async/go
      (while @ws
        (let [article-req (async/<! search/article-reqs)]
          (-> article-req :id url/article str (URL.) open))))

    (.addViewComponentCustomizer
     app-ws
     (proxy [ViewComponentCustomizer] []
       (customizeView [viewInfo]
         (when (= "zdl-lex-client" (.getViewID viewInfo))
           (doto viewInfo
             (.setTitle "ZDL/DWDS")
             ;;(.setIcon nil)
             (.setComponent metasearch))))))

    (.addToolbarComponentsCustomizer
     app-ws
     (proxy [ToolbarComponentsCustomizer] []
       (customizeToolbar [toolbarInfo]
         (when (= "zdl-lex-client" (.getToolbarID toolbarInfo))
           (doto toolbarInfo
             (.setTitle "ZDL/DWDS")
             (.setComponents (into-array JComponent toolbar)))))))))

(defn -applicationClosing [this]
  (reset! ws nil)
  true)

