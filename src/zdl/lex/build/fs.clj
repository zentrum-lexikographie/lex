(ns zdl.lex.build.fs
  (:require [zdl.lex.fs :refer [file]])
  (:import java.io.File))

(def ^File scripts-dir
  (file "scripts"))

(def ^File project-dir
  (file "."))

(def ^File version-edn
  (file project-dir "src" "version.edn"))

(def ^File oxygen-dir
  (file project-dir "oxygen"))

(def ^File client-jar
  (file oxygen-dir "plugin" "lib" "org.zdl.lex.client.jar"))

(def ^File cli-jar
  (file project-dir "build" "org.zdl.lex.cli.jar"))

(def ^File server-jar
  (file project-dir "docker" "server" "org.zdl.lex.server.jar"))

