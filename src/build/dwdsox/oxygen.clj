(ns dwdsox.oxygen
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs])
  (:import [java.io File]))

(def oxygen-home (->>
                  (concat [(File. (or (System/getenv "OXYGEN_HOME") "./oxygen"))]
                           (fs/find-files "/usr/local" #"Oxygen XML Editor [0-9\.]+"))
                  (filter #(.isDirectory %))
                  (map #(.getAbsolutePath %))
                  (first)))

(defn -main [& args]
  (let [pb (ProcessBuilder.
            ["java"
             "-Dcom.oxygenxml.editor.plugins.dir=target/oxygen/plugins"
             "-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor"
             "-cp" (string/join ":" [(str oxygen-home "/lib/oxygen.jar")
                                     (str oxygen-home "/classes")
                                     oxygen-home])
             "ro.sync.exml.Oxygen"
             "target/oxygen/test-project/project.xpr"])]
    (.. pb (environment) (put "OXYGEN_HOME" oxygen-home))
    (doto pb (.inheritIO) (.start))))
