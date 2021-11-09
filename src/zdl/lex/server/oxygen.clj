(ns zdl.lex.server.oxygen
  (:require [clojure.data.xml :as dx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.walk :refer [postwalk]])
  (:import [io.github.classgraph ClassGraph ScanResult]
           java.nio.charset.Charset
           [java.util.zip ZipEntry ZipOutputStream]))

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
    (postwalk
     (fn [node]
       (if (map? node)
         (cond-> node
           (= (node :tag) ::xt/extensions)
           (update :attrs merge update-descriptor-namespaces)
           (= (node :tag) ::xt/version)
           (assoc :content (list version)))
         node))
     (dx/parse is))))

(def plugin-descriptor
  (with-open [is (io/input-stream (io/resource "plugin/plugin.xml"))]
    (->
     (dx/parse is :support-dtd false)
     (assoc-in [:attrs :version] version)
     (dx/emit-str))))

(defn classpath-resources
  [path]
  (with-open [scan (.. (ClassGraph.)
                       (whitelistPaths (into-array String [path]))
                       (scan))]
    (let [path-pattern (re-pattern (str "^" path "/?"))]
      (vec
       (for [resource (.. ^ScanResult scan (getAllResources) (asMap))]
         {:path     (-> resource .getKey (str/replace path-pattern ""))
          :resource (-> resource .getValue first)})))))

(comment
  (classpath-resources "framework"))

(defn write-to-zip [zip path-prefix path stream]
  (.. zip (putNextEntry (ZipEntry. (str path-prefix path))))
  (io/copy stream zip)
  (.. zip (closeEntry)))

(defn download-framework
  [stream]
  (try
    (with-open [zip (ZipOutputStream. stream zip-charset)]
      (let [write-to-zip (partial write-to-zip zip "zdl-lex-client/")]
        (doseq [{:keys [resource path]} (classpath-resources "framework")]
          (with-open [stream (.open resource)]
            (write-to-zip path stream)))))
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
        (doseq [{:keys [resource path]} (classpath-resources "plugin/lib")]
          (with-open [stream (.open resource)]
            (write-to-zip "zdl-lex-client/lib/" path stream)))))
    (catch Exception e
      (log/warn e))))

