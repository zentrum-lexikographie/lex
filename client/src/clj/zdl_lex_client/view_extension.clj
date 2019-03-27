(ns zdl-lex-client.view-extension
  (:require [zdl-lex-client.metasearch :as metasearch])
  (:import [ro.sync.exml.workspace.api.standalone ViewComponentCustomizer])
  (:gen-class
   :name de.zdl.oxygen.ViewExtension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension]
   :state state
   :init init))

(defn -init []
  [[] (ref {:ws-access (atom nil)})])

(defn -applicationStarted [this app-ws-access]
  (let [{:keys [ws-access]} @(.state this)]
    (reset! ws-access app-ws-access))
  (.addViewComponentCustomizer
   app-ws-access
   (proxy [ViewComponentCustomizer] []
     (customizeView [viewInfo]
       (when (= "zdl-lex-client" (.getViewID viewInfo))
         (doto viewInfo
           (.setTitle "DWDS/ZDL")
           ;;(.setIcon nil)
           (.setComponent (metasearch/form))))))))

(defn -applicationClosing [this]
  (let [{:keys [ws-access]} @(.state this)]
    (when-not (nil? @ws-access)
      (reset! ws-access nil)))
  true)

