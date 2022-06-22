(ns zdl.lex.fs
  (:require [clojure.java.io :as io])
  (:import java.io.File
           java.nio.file.CopyOption
           java.nio.file.Files
           java.nio.file.Path))

(extend-protocol io/Coercions
  Path
  (as-file [^Path p] (.toFile p))
  (as-url [^Path p] (.. p (toFile) (toURI) (toURL))))

(defn file
  ^File [& args]
  (let [^File f (apply io/file args)]
    (.getCanonicalFile f)))

(defn file?
  [& args]
  (.isFile ^File (apply file args)))

(defn xml-file?
  [& args]
  (let [^File f (apply file args)]
    (and (file? f) (.. f (getName) (endsWith ".xml")))))

(defn directory?
  [& args]
  (.isDirectory ^File (apply file args)))

(defn path
  ^String [& args]
  (.getPath (apply file args)))

(defn path-obj
  ^Path [& args]
  (.toPath (apply file args)))

(defn relativize
  ^Path [base f]
  (.relativize (path-obj base) (path-obj f)))

(defn resolve-path
  ^Path [base ^Path p]
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

(defn ensure-dirs
  [& d]
  (doto (apply file d) (.mkdirs)))

(defn clear-dir!
  [^File d]
  (when (directory? d) (delete! d))
  (ensure-dirs d))
