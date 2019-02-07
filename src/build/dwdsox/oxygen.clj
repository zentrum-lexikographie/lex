(ns dwdsox.oxygen
  (:require [clojure.string :as string]))

(defn -main [& args]
  (let [home (System/getenv "OXYGEN_HOME")]
    (doto (ProcessBuilder.
           ["java"
            "-Dcom.oxygenxml.editor.plugins.dir=target/oxygen/plugins"
            "-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor"
            "-cp" (string/join ":" [(str home "/lib/oxygen.jar")
                                    (str home "/classes")
                                    home])
            "ro.sync.exml.Oxygen"])
      (.inheritIO)
      (.start))))
