(ns zdl.lex.data
  (:require [zdl.lex.env :refer [getenv]]
            [zdl.lex.fs :as fs])
  (:import java.io.File))

(def base-dir
  (getenv "DATA_DIR" "zdl-lex-data"))

(defn- ^File file*
  [& args]
  (apply fs/file base-dir args))

(defn assert-dir
  [^File d]
  (.mkdirs d)
  (when-not (.isDirectory d) (throw (IllegalArgumentException. d)))
  d)

(defn ^File dir
  [& args]
  (let [^File f (apply file* args)]
    (assert-dir f)))

(defn ^File file
  [& args]
  (let [^File f (apply file* args)]
    (assert-dir (.getParentFile f))
    f))
