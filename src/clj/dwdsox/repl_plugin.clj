(ns dwdsox.repl-plugin
  (:require [nrepl.server :as repl]
            [dwdsox.exist-db :as db])
  (:gen-class
   :implements [ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension]
   :state state
   :init init))

(def sample-xquery "
xquery version \"1.0\";
let $message := 'Hello World!'
return
<results>
  <message>{$message}</message>
</results>")

(defonce ws-access (atom nil))

(defn -init []
  [[] (ref {:server (atom nil)})])

(defn -applicationStarted [this app-ws-access]
  (when-let [port (-> (System/getProperty "dwdsox.repl.port") Integer/parseInt)]
    (let [{:keys [server]} @(.state this)]
      (reset! server (repl/start-server :port port)))
    (reset! ws-access app-ws-access))
  (println (db/query sample-xquery)))

(defn -applicationClosing [this]
  (let [{:keys [server]} @(.state this)]
    (when-not (nil? @server)
      (@server)
      (reset! server nil)))
  (reset! ws-access nil)
  true)

