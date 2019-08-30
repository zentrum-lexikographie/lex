(ns zdl-lex-build.zip
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs])
  (:import java.nio.charset.Charset
           java.nio.file.Path
           [java.util.zip ZipEntry ZipFile ZipOutputStream]))

(def ^Charset utf-8-charset (Charset/forName "UTF-8"))

(defn ^Path f->p [f] (.. (io/file f) (toPath)))

(defn zip [path dir prefix]
  (with-open [out (io/output-stream (fs/file path))
              zip (ZipOutputStream. out utf-8-charset)]
    (let [prefix-path (f->p prefix)
          dir-path (f->p dir)]
      (doseq [entry (file-seq dir)
              :when (fs/file? entry)
              :let [entry-path (f->p entry)
                    relative-path (.. dir-path (relativize entry-path))
                    archive-path (.. prefix-path (resolve relative-path))]]
        (.. zip (putNextEntry (ZipEntry. (str archive-path))))
        (io/copy entry zip)
        (.. zip (closeEntry))))))

(defn unzip [path dir]
  (with-open [zip (ZipFile. (fs/file path) utf-8-charset)]
    (doseq [entry (enumeration-seq (.entries zip))
            :when (not (.isDirectory ^ZipEntry entry))
            :let [f (fs/file dir (str entry))]]
      (fs/mkdirs (fs/parent f))
      (io/copy (.getInputStream zip entry) f)))
  dir)
