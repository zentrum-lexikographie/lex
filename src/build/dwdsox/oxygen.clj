(ns dwdsox.oxygen
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs])
  (:import [java.io File]))

(defn find-oxygen-home []
  (or
   (->>
    (concat [(File. (or (System/getenv "OXYGEN_HOME") "oxygen"))]
            (fs/find-files "/usr/local" #"Oxygen XML Editor [0-9\.]+"))
    (filter #(.isDirectory %))
    (map #(.getAbsolutePath %))
    (first))
   (throw (IllegalStateException. "Cannot find $OXYGEN_HOME."))))

(defn run-oxygen [oxygen-home]
  (let [oxygen
        (ProcessBuilder.
         ["java"
          "-Ddwdsox.repl.port=7000"
          "-Dcom.oxygenxml.editor.plugins.dir=src/oxygen"
          "-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor"
          "-cp" (string/join ":" [(str oxygen-home "/lib/oxygen.jar")
                                  (str oxygen-home "/classes")
                                  oxygen-home])
          "ro.sync.exml.Oxygen"
          "test-project.xpr"])]
    (.. oxygen (environment) (put "OXYGEN_HOME" oxygen-home))
    (doto oxygen (.inheritIO) (.start)))
  oxygen-home)

(defn -main [& args]
  (-> (find-oxygen-home) (run-oxygen)))
