(ns zdl-lex-client.view-extension
  (:gen-class
   :name de.zdl.oxygen.ViewExtension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension])
  (:require [clojure.core.async :as async]
            [zdl-lex-client.article :as article]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.metasearch :as metasearch]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.url :as url]
            [zdl-lex-client.http :as http])
  (:import java.net.URL
           javax.swing.JComponent
           [ro.sync.exml.workspace.api.standalone ToolbarComponentsCustomizer ViewComponentCustomizer]
           ro.sync.exml.workspace.api.standalone.ui.ToolbarButton))

(defonce ws (atom nil))

(defonce open-articles
  (async/go-loop []
    (let [article-req (async/<! bus/article-reqs)
          article-url (-> article-req :id url/article str (URL.))]
      (some-> @ws (.open article-url)))
    (recur)))

(defn -applicationStarted [this app-ws]
  (reset! ws app-ws)
  (let [metasearch (metasearch/form)

        article-search (ToolbarButton. search/action false)
        article-create (ToolbarButton. article/create false)
        article-delete (ToolbarButton. article/delete false)

        toolbar [icon/logo article-search search/input article-create article-delete]]

    (.addViewComponentCustomizer
     app-ws
     (proxy [ViewComponentCustomizer] []
       (customizeView [viewInfo]
         (when (= "zdl-lex-client-view" (.getViewID viewInfo))
           (doto viewInfo
             (.setTitle "ZDL/DWDS")
             ;;(.setIcon nil)
             (.setComponent metasearch))))))

    (.addToolbarComponentsCustomizer
     app-ws
     (proxy [ToolbarComponentsCustomizer] []
       (customizeToolbar [toolbarInfo]
         (when (= "zdl-lex-client-toolbar" (.getToolbarID toolbarInfo))
           (doto toolbarInfo
             (.setTitle "ZDL/DWDS")
             (.setComponents (into-array JComponent toolbar)))))))))

(defn -applicationClosing [this]
  (reset! ws nil)
  true)

