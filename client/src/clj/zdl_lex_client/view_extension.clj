(ns zdl-lex-client.view-extension
  (:require [zdl-lex-client.metasearch :as metasearch]
            [zdl-lex-client.quicksearch :as quicksearch]
            [zdl-lex-client.url :as url])
  (:import [java.net URL]
           [javax.swing JComponent]
           [ro.sync.exml.workspace.api.standalone
            ViewComponentCustomizer ToolbarComponentsCustomizer])
  (:gen-class
   :name de.zdl.oxygen.ViewExtension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension]))

(def ws (atom nil))

(defn with-ws [f] (some-> @ws f))

(defn open [url]
  (with-ws #(.open % (URL. (str url)))))

(defn -applicationStarted [this app-ws]
  (reset! ws app-ws)
  (let [metasearch (metasearch/form)
        quicksearch-handler #(some-> % :id url/article open)
        quicksearch (quicksearch/input quicksearch-handler)]
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
             (.setComponents (into-array JComponent [quicksearch])))))))))

(defn -applicationClosing [this]
  (when-not (nil? @ws) (reset! ws nil))
  true)

