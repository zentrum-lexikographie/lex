(ns zdl.lex.server.oxygen
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.zip :as zip]
   [gremid.data.xml :as dx]
   [gremid.data.xml.io :as dx.io]
   [gremid.data.xml.zip :as dx.zip])
  (:import
   (io.github.classgraph ClassGraph ScanResult)
   (java.nio.charset Charset)
   (java.util.zip ZipEntry ZipOutputStream)
   (javax.xml.stream XMLInputFactory)))

(def ^Charset zip-charset (Charset/forName "UTF-8"))

(def version
  (or (some-> "version.edn" io/resource slurp read-string :version)
      "000000.00.00"))

(dx/alias-uri :xt "http://www.oxygenxml.com/ns/extension")

(def update-descriptor-namespaces
  {:xmlns/xt "http://www.oxygenxml.com/ns/extension"
   :xmlns/xsi "http://www.w3.org/2001/XMLSchema-instance"})

(def update-descriptor
  (with-open [is (io/input-stream (io/resource "updateSite.xml"))]
    (loop [doc (dx/pull-all (dx/parse is))]
      (if-let [version-el (dx.zip/xml1-> (zip/xml-zip doc)
                                  ::xt/extensions ::xt/extension
                                  ::xt/version
                                  [(complement (dx.zip/text= version))])]
        (recur (zip/root (zip/edit version-el
                                   #(assoc % :content (list version)))))
        doc))))

(def plugin-descriptor-input-factory
  (doto (dx.io/new-input-factory)
    (.setProperty XMLInputFactory/SUPPORT_DTD false)))

(def plugin-descriptor
  (with-open [is (io/input-stream (io/resource "plugin/plugin.xml"))]
    (let [doc (zip/xml-zip (dx/parse plugin-descriptor-input-factory is))
          plugin-el (dx.zip/xml1-> doc :plugin)
          plugin-el (zip/edit plugin-el #(assoc-in % [:attrs :version] version))]
      (dx/emit-str (zip/root plugin-el)))))

(defn scan-classpath!
  [path]
  (.. (ClassGraph.)
      (disableNestedJarScanning)
      (whitelistPaths (into-array String [path]))
      (scan)))

(defn write-to-zip [zip path-prefix path stream]
  (.. zip (putNextEntry (ZipEntry. (str path-prefix path))))
  (io/copy stream zip)
  (.. zip (closeEntry)))

(defn download-framework
  [stream]
  (try
    (with-open [zip (ZipOutputStream. stream zip-charset)]
      (let [write-to-zip (partial write-to-zip zip "zdl-lex-client/")]
        (with-open [scan (scan-classpath! "framework")]
          (doseq [r (.. ^ScanResult scan (getAllResources) (asMap))]
            (let [path (-> r .getKey (str/replace #"^framework/?" ""))]
              (with-open [stream (-> r .getValue first .open)]
                (write-to-zip path stream)))))))
    (catch Exception e
      (log/warn e))))

(defn download-plugin
  [stream]
  (try
    (with-open [zip (ZipOutputStream. stream zip-charset)]
      (let [write-to-zip (partial write-to-zip zip)
            descriptor   (.. plugin-descriptor (getBytes "UTF-8"))]
        (with-open [stream (io/input-stream descriptor)]
          (write-to-zip "zdl-lex-client/" "plugin.xml" stream))
        (with-open [scan (scan-classpath! "plugin/lib")]
          (doseq [r (.. ^ScanResult scan (getAllResources) (asMap))]
            (let [path (-> r .getKey (str/replace #"^plugin/lib/?" ""))]
              (with-open [stream (-> r .getValue first .open)]
                (write-to-zip "zdl-lex-client/lib/" path stream)))))))
    (catch Exception e
      (log/warn e))))
