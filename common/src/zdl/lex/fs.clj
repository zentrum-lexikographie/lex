(ns zdl.lex.fs
  (:require [clojure.java.io :as io])
  (:import java.io.File
           java.nio.file.CopyOption
           java.nio.file.Files
           java.nio.file.Path))

(defn ^File file
  [& args]
  (let [^File f (apply io/file args)]
    (.getCanonicalFile f)))

(defn ^String path
  [& args]
  (.getPath (apply file args)))

(defn ^Path path-obj
  [& args]
  (.toPath (apply file args)))

(defn ^Path relativize
  [base f]
  (.relativize (path-obj base) (path-obj f)))

(defn ^Path resolve-path
  [base ^Path p]
  (.resolve (path-obj base) p))

(def ^:private copy-options
  (make-array CopyOption 0))

(defn copy
  [src dest]
  (let [^File dest (file dest)
        ^File dest-parent (.getParentFile dest)]
    (.mkdirs dest-parent)
    (Files/copy (path-obj src) (path-obj dest) copy-options)))

(defn delete!
  [^File f & [silently]]
  (when (.isDirectory f)
    (doseq [^File c (.listFiles f)] (delete! c)))
  (io/delete-file f silently))

(defn clear-dir!
  [^File d]
  (when (.isDirectory d) (delete! d))
  (.mkdirs d))
