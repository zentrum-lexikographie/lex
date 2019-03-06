(ns dwdsox.repl-extension
  (:require [nrepl.server :as repl]
            [dwdsox.exist-db :as db]
            [clojure.java.io :as io])
  (:gen-class
   :name de.dwds.zdl.oxygen.ReplExtension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension]
   :state state
   :init init))

(defn -init []
  [[] (ref {:server (atom nil)})])

(defn -applicationStarted [this app-ws-access]
  (when-let [port (some-> (System/getProperty "dwdsox.repl.port") Integer/parseInt)]
    (let [{:keys [server]} @(.state this)]
      (reset! server (repl/start-server :port port)))))

(defn -applicationClosing [this]
  (let [{:keys [server]} @(.state this)]
    (when-not (nil? @server)
      (repl/stop-server @server)
      (reset! server nil)))
  true)

