(ns zdl-lex-server.oxygen
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.util.http-response :as htstatus]
            [ring.util.io :as rio]
            [zdl-lex-common.xml :as xml]
            [taoensso.timbre :as timbre])
  (:import java.nio.charset.Charset
           [java.util.zip ZipEntry ZipOutputStream]
           io.github.classgraph.ClassGraph))

(def ^Charset zip-charset (Charset/forName "UTF-8"))

(def version (-> "version.edn" io/resource slurp read-string :version))

(defn generate-update-descriptor [_]
  (let [descriptor (-> "updateSite.xml" io/resource xml/->dom)
        elements-by-name #(-> (.getElementsByTagName descriptor %) xml/->seq)]
    (doseq [xt-version (elements-by-name "xt:version")]
      (.. xt-version (setTextContent version)))
    (htstatus/ok (xml/serialize descriptor))))

(defn generate-plugin-descriptor []
  (let [descriptor (-> "plugin/plugin.xml" io/resource xml/->dom)
        plugin-el (.. descriptor (getDocumentElement))]
    (.. plugin-el (setAttribute "version" version))
    (xml/serialize descriptor)))


(defmacro with-classpath-resources [path & body]
  `(let [path-pattern# (re-pattern (str "^" ~path "/?"))
         paths# (into-array String [~path])]
     (with-open [cp-scan# (.. (ClassGraph.) (whitelistPaths paths#) (scan))]
       (doseq [e# (.. cp-scan# (getAllResources) (asMap))
               :let  [~'path (-> e# .getKey (str/replace path-pattern# ""))
                      ~'resource (-> e# .getValue first)]]
         ~@body))))

(defn write-to-zip [zip path-prefix path stream]
  (.. zip (putNextEntry (ZipEntry. (str path-prefix path))))
  (io/copy stream zip)
  (.. zip (closeEntry)))

(defn download-plugin [_]
  (-> (fn [stream]
        (try
          (with-open [zip (ZipOutputStream. stream zip-charset)]
            (let [write-to-zip (partial write-to-zip zip)
                  descriptor (.. (generate-plugin-descriptor) (getBytes "UTF-8"))]
              (with-open [stream (io/input-stream descriptor)]
                (write-to-zip "zdl-lex-client/" "plugin.xml" stream))
              (with-classpath-resources "plugin/lib"
                (with-open [stream (.. resource (open))]
                  (write-to-zip "zdl-lex-client/lib/" path stream)))))
          (catch Exception e (timbre/warn e))))
      (rio/piped-input-stream)
      (htstatus/ok)))

(defn download-framework [_]
  (-> (fn [stream]
        (try
          (with-open [zip (ZipOutputStream. stream zip-charset)]
            (let [write-to-zip (partial write-to-zip zip "zdl-lex-client/")]
              (with-classpath-resources "framework"
                (with-open [stream (.. resource (open))]
                  (write-to-zip path stream)))))
          (catch Exception e (timbre/warn e))))
      (rio/piped-input-stream)
      (htstatus/ok)))

(def ring-handlers
  ["/oxygen"
   ["/updateSite.xml" {:get generate-update-descriptor}]
   ["/zdl-lex-plugin.zip" {:get download-plugin}]
   ["/zdl-lex-framework.zip" {:get download-framework}]])

(comment
  (-> (download-plugin nil) :body slurp count)
  (-> (download-framework nil) :body slurp count))
