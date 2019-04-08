(ns zdl-lex-client.repl-extension
  (:require [nrepl.server :as repl]
            [zdl-lex-client.env :refer [config]])
  (:gen-class
   :name de.zdl.oxygen.ReplExtension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension]
   :state state
   :init init))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn -init []
  [[] (ref {:server (atom nil)})])

(defn -applicationStarted [this app-ws-access]
  (when-let [port (get-in config [:repl :port])]
    (let [{:keys [server]} @(.state this)]
      (reset! server (repl/start-server :port port :handler (nrepl-handler))))))

(defn -applicationClosing [this]
  (let [{:keys [server]} @(.state this)]
    (when-not (nil? @server)
      (repl/stop-server @server)
      (reset! server nil)))
  true)

