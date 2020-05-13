(ns zdl.lex.build.fs
  (:require [clojure.java.io :as io]
            [zdl.lex.fs :refer [file]])
  (:import java.io.File))

(def ^File scripts-dir
  (file "scripts"))

(def ^File project-dir
  (file ".."))

(def ^File version-edn
  (file project-dir "common" "src" "version.edn"))

(def ^File client-dir
  (file project-dir "client"))

(def ^File cli-dir
  (file project-dir "cli"))

(def ^File server-dir
  (file project-dir "server"))

(def ^File oxygen-dir
  (file project-dir "oxygen"))

(def ^File client-jar
  (file oxygen-dir "plugin" "lib" "org.zdl.lex.client.jar"))

(def ^File cli-jar
  (file cli-dir "build" "org.zdl.lex.cli.jar"))

(def ^File server-jar
  (file project-dir "docker" "server" "org.zdl.lex.server.jar"))

