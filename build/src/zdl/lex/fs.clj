(ns zdl.lex.fs
  (:require [clojure.java.io :as io])
  (:import java.io.File))

(defn ^File file
  [& args]
  (let [^File f (apply io/file args)]
    (.getCanonicalFile f)))

(defn ^String path
  [& args]
  (.getPath (apply file args)))

(defn delete!
  [^File f]
  (doseq [^File f (reverse (file-seq f))] (.delete f)))

(defn clear-dir!
  [^File d]
  (when (.isDirectory d) (delete! d))
  (.mkdirs d))

(def ^File scripts-dir
  (file "scripts"))

(def ^File project-dir
  (file ".."))

(def ^File version-edn
  (file project-dir "common" "src" "version.edn"))

(def ^File client-dir
  (file project-dir "client"))

(def ^File server-dir
  (file project-dir "server"))

(def ^File oxygen-dir
  (file project-dir "oxygen"))

(def ^File client-jar
  (file oxygen-dir "plugin" "lib" "org.zdl.lex.client.jar"))

(def ^File server-jar
  (file project-dir "docker" "server" "org.zdl.lex.server.jar"))

