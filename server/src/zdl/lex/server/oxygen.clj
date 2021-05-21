(ns zdl.lex.server.oxygen
  (:require [clojure.data.xml :as dx]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.walk :refer [postwalk]]
            [ring.util.io :as rio])
  (:import io.github.classgraph.ClassGraph
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
    (dx/emit-str
     (postwalk
      (fn [node]
        (if (map? node)
          (cond-> node
            (= (node :tag) ::xt/extensions)
            (update :attrs merge update-descriptor-namespaces)
            (= (node :tag) ::xt/version)
            (assoc :content (list version)))
          node))
      (dx/parse is)))))

(def plugin-descriptor
  (with-open [is (io/input-stream (io/resource "plugin/plugin.xml"))]
    (->
     (dx/parse is :support-dtd false)
     (assoc-in [:attrs :version] version)
     (dx/emit-str))))

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

(defn download-framework
  [stream]
  (try
    (with-open [zip (ZipOutputStream. stream zip-charset)]
      (let [write-to-zip (partial write-to-zip zip "zdl-lex-client/")]
        (with-classpath-resources "framework"
          (with-open [stream (.. resource (open))]
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
        (with-classpath-resources "plugin/lib"
          (with-open [stream (.. resource (open))]
            (write-to-zip "zdl-lex-client/lib/" path stream)))))
    (catch Exception e
      (log/warn e))))

