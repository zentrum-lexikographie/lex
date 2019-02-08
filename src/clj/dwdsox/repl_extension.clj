(ns dwdsox.repl-extension
  (:require [nrepl.server :as repl]
            [dwdsox.exist-db :as db]
            [clojure.java.io :as io])
  (:gen-class
   :name de.dwds.zdl.oxygen.ReplExtension
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension]
   :state state
   :init init))

(def sample-xquery (-> "dwdsox/xquery/hello.xq" io/resource slurp))

(defonce ws-access (atom nil))

(defn -init []
  [[] (ref {:server (atom nil)})])

(defn -applicationStarted [this app-ws-access]
  (when-let [port (-> (System/getProperty "dwdsox.repl.port") Integer/parseInt)]
    (let [{:keys [server]} @(.state this)]
      (reset! server (repl/start-server :port port)))
    (reset! ws-access app-ws-access))
  (println (db/query sample-xquery))
  (println (db/get "indexedvalues.xml")))

(defn -applicationClosing [this]
  (let [{:keys [server]} @(.state this)]
    (when-not (nil? @server)
      (@server)
      (reset! server nil)))
  (reset! ws-access nil)
  true)

