(ns dwdsox.repl-plugin
  (:require [nrepl.server :as repl])
  (:gen-class
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension]
   :state state
   :init init))

(defonce ws-access (atom nil))

(defn -init []
  [[] (ref {:server (atom nil)})])

(defn -applicationStarted [this app-ws-access]
  (when (= "true" (System/getProperty "dwdsox.repl" "false"))
    (reset! ws-access app-ws-access)
    (let [{:keys [server]} @(.state this)]
      (reset! server (repl/start-server :port 5555)))))

(defn -applicationClosing [this]
  (let [{:keys [server]} @(.state this)]
    (when-not (nil? @server)
      (@server)
      (reset! server nil)))
  (reset! ws-access nil)
  true)

