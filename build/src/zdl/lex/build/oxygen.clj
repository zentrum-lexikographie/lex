(ns zdl.lex.build.oxygen
  (:require [zdl.lex.fs :refer [file path]]
            [zdl.lex.build.fs :refer [oxygen-dir]]
            [zdl.lex.sh :refer [sh!]]
            [zdl.lex.build :as build]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import java.io.File))

(defn- oxygen-local-installs
  []
  (->> (.listFiles (file "/usr/local"))
       (filter #(str/starts-with? (.getName ^File %) "Oxygen XML Editor"))
       (map path)
       (sort #(compare %2 %1))))

(def oxygen-home
  (->> (oxygen-local-installs)
       (concat [(System/getenv "OXYGEN_HOME")
                (path (System/getProperty "user.home") "oxygen")])
       (remove nil?)
       (map file) (filter #(.isDirectory ^File %))
       (map path) (first)))

(defn -main
  [& args]
  (try
    (when-not oxygen-home
      (log/error "Cannot locate $OXYGEN_HOME")
      (System/exit 1))
    (build/client!)
    (log/info [oxygen-home (path oxygen-dir)])
    (->>
     (..
      (ProcessBuilder.
       [(path oxygen-home "jre" "bin" "java")
        "-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor"
        (str "-Dcom.oxygenxml.editor.plugins.dir=" (path oxygen-dir))
        "-cp" (->> [(path oxygen-home "lib" "oxygen.jar")
                    (path oxygen-home "lib" "oxygen-basic-utilities.jar")
                    (path oxygen-home "classes")
                    (path oxygen-home)]
                   (str/join \:))
        "ro.sync.exml.Oxygen" "test-project.xpr"])
      (directory oxygen-dir)
      (inheritIO)
      (start)
      (waitFor))
     (System/exit))
    (finally (shutdown-agents))))
