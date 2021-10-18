(ns zdl.lex.build.oxygen
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [zdl.lex.build :as build]
            [zdl.lex.build.fs :refer [oxygen-dir]]
            [zdl.lex.fs :refer [file path]]
            [zdl.lex.util :refer [exec!]])
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

(defn start
  [_]
  (if-not oxygen-home
    (log/error "Cannot locate $OXYGEN_HOME")
    (do
      (build/package-client!)
      (log/infof "Starting Oxygen XML Editor @%s (project: %s)"
                 oxygen-home (path oxygen-dir))
      (->>
       (..
        (ProcessBuilder.
         [(path oxygen-home "jre" "bin" "java")
          "--illegal-access=permit"
          "--add-opens=java.base/java.net=ALL-UNNAMED"
          "--add-opens=java.base/java.lang=ALL-UNNAMED"
          "--add-opens=java.desktop/java.awt=ALL-UNNAMED"
          "--add-opens=java.desktop/javax.swing=ALL-UNNAMED"
          "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED"
          "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED"
          "-Xmx1g"
          "-XX:-OmitStackTraceInFastThrow"
          "-XX:SoftRefLRUPolicyMSPerMB=10"
          (str "-Dcom.oxygenxml.editor.plugins.dir=" (path oxygen-dir))
          "-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor"
          "-Djava.net.preferIPv4Stack=true"
          "-Dsun.io.useCanonCaches=true"
          "-Dsun.io.useCanonPrefixCache=true"
          "-cp" (->> [(path oxygen-home "classes")
                      (path oxygen-home "lib" "oxygen.jar")
                      (path oxygen-home "lib" "oxygen-basic-utilities.jar")
                      (path oxygen-home)]
                     (str/join \:))
          "ro.sync.exml.Oxygen"
          "test-project.xpr"])
        (directory oxygen-dir)
        (inheritIO)
        (start)
        (waitFor))))))

(def start!
  (partial exec! start))
