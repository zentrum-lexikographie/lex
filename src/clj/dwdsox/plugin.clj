(ns dwdsox.plugin
  (:require [nrepl.server :as repl]
            [dwdsox.exist-db :as db]
            [clojure.java.io :as io])
  (:gen-class
   :name de.dwds.zdl.oxygen.Plugin
   :extends ro.sync.exml.plugin.Plugin
   :constructors {[ro.sync.exml.plugin.PluginDescriptor]
                  [ro.sync.exml.plugin.PluginDescriptor]}
   :post-init init))

(defonce descriptor (atom nil))

(defn -init [this app-descriptor]
  (reset! descriptor app-descriptor))
