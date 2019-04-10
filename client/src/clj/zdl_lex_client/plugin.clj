(ns zdl-lex-client.plugin
  (:gen-class
   :name de.zdl.oxygen.Plugin
   :extends ro.sync.exml.plugin.Plugin
   :constructors {[ro.sync.exml.plugin.PluginDescriptor]
                  [ro.sync.exml.plugin.PluginDescriptor]}
   :post-init init))

()*use-context-classloader*
(defonce descriptor (atom nil))

(defn -init [this app-descriptor]
  (reset! descriptor app-descriptor))
