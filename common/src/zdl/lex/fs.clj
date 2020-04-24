(ns zdl.lex.fs
  (:require [zdl.lex.env :refer [getenv]]
            [zdl.lex.util :refer [file]])
  (:import java.io.File))

(def base-dir
  (delay (getenv ::data-dir "ZDL_LEX_DATA_DIR" "zdl-lex-data")))

(defn- ^File data-file*
  [& args]
  (apply file @base-dir args))

(defn assert-dir
  [^File d]
  (.mkdirs d)
  (when-not (.isDirectory d) (throw (IllegalArgumentException. d)))
  d)

(defn ^File data-dir
  [& args]
  (let [^File f (apply data-file* args)]
    (assert-dir f)))

(defn ^File data-file
  [& args]
  (let [^File f (apply data-file* args)]
    (assert-dir (.getParentFile f))
    f))
