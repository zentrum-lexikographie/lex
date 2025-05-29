(ns zdl.lex.server.oxygen
  (:require
   [babashka.fs :as fs]
   [clojure.java.io :as io]
   [clojure.walk]
   [gremid.xml :as gx]
   [taoensso.telemere :as tm]
   [tick.core :as t])
  (:import
   (java.nio.charset Charset)
   (java.util.zip ZipEntry ZipOutputStream)
   (javax.xml.stream XMLInputFactory)))

(def ^Charset zip-charset
  (Charset/forName "UTF-8"))

(def version
  (or
   (try (slurp (fs/file "oxygen" "VERSION")) (catch Throwable t (tm/error! t) nil))
   (t/format "yyyyMM.dd.HHmm" (t/in (t/now) "UTC"))))

(def update-descriptor
  (with-open [is (io/input-stream (fs/file "oxygen" "updateSite.xml"))]
    (->> is (gx/read-events) (gx/events->node)
         (clojure.walk/postwalk
          (fn [{:keys [tag] :as v}]
            (cond-> v
              (and (= :xt:version tag) (= "[VERSION]" (gx/text v)))
              (assoc :content (list version)))))
         (gx/node->events) (gx/write-events *out*) (with-out-str))))

(def plugin-descriptor-input-factory
  (doto (XMLInputFactory/newInstance)
    (.setProperty XMLInputFactory/IS_NAMESPACE_AWARE false)
    (.setProperty XMLInputFactory/SUPPORT_DTD false)))

(def plugin-descriptor
  (with-open [is (io/input-stream (fs/file "oxygen" "plugin" "plugin.xml"))]
    (binding [gx/*input-factory* plugin-descriptor-input-factory]
      (->> is (gx/read-events) (gx/events->node)
           (clojure.walk/postwalk
            (fn [{:keys [tag] :as v}]
              (cond-> v (= :plugin tag) (assoc-in [:attrs :version] version))))
           (gx/node->events) (gx/write-events *out*) (with-out-str)))))

(defn write-to-zip [zip path-prefix path stream]
  (.. zip (putNextEntry (ZipEntry. (str path-prefix path))))
  (io/copy stream zip)
  (.. zip (closeEntry)))

(defn write-tree-to-zip
  [write-to-zip base-dir]
  (fs/walk-file-tree
   base-dir
   {:visit-file (fn [f _]
                  (let [path (str (fs/relativize base-dir f))]
                    (with-open [stream (io/input-stream (fs/file f))]
                      (write-to-zip path stream)))
                  :continue)}))

(defn download-framework
  [stream]
  (try
    (with-open [zip (ZipOutputStream. stream zip-charset)]
      (write-tree-to-zip (partial write-to-zip zip "zdl-lex-client/")
                         (fs/path "oxygen" "framework")))
    (catch Exception e (tm/error! e))))

(defn download-plugin
  [stream]
  (try
    (with-open [zip (ZipOutputStream. stream zip-charset)]
      (let [write-to-zip (partial write-to-zip zip)
            descriptor   (.. plugin-descriptor (getBytes "UTF-8"))]
        (with-open [stream (io/input-stream descriptor)]
          (write-to-zip "zdl-lex-client/" "plugin.xml" stream))
        (write-tree-to-zip (partial write-to-zip "zdl-lex-client/lib/")
                           (fs/path "oxygen" "plugin" "lib"))))
    (catch Exception e (tm/error! e))))
