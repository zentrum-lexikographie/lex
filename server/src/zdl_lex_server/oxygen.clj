(ns zdl-lex-server.oxygen
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cpath-clj.core :as cp]
            [ring.util.http-response :as htstatus]
            [ring.util.io :as rio]
            [zdl-lex-common.xml :as xml]
            [taoensso.timbre :as timbre])
  (:import java.nio.charset.Charset
           [java.util.zip ZipEntry ZipOutputStream]))

(def ^Charset zip-charset (Charset/forName "UTF-8"))

(defn generate-update-descriptor [_]
  (let [{:keys [version]} (-> "version.edn" io/resource slurp read-string)
        descriptor (-> "oxygen/updateSite.xml" io/resource xml/->dom)
        elements-by-name #(-> (.getElementsByTagName descriptor %) xml/->seq)]
    (doseq [xt-version (elements-by-name "xt:version")]
      (.. xt-version (setTextContent version)))
    (htstatus/ok (xml/serialize descriptor))))

(defn classpath-resources
  ([prefix]
   (classpath-resources prefix (constantly true)))
  ([prefix include-path?]
   (for [[path uri] (cp/resources prefix)
         :let [path (str/replace path #"^/" "")]
         :when (include-path? path)]
     [path (first uri)])))

(defn download-plugin [_]
  (-> (fn [stream] (spit stream (vec (classpath-resources "oxygen/plugin/lib/"
                                                          (constantly true)))))
      (rio/piped-input-stream)
      (htstatus/ok)))

(defn download-framework [_]
  (-> (fn [stream]
        (try
          (with-open [zip (ZipOutputStream. stream zip-charset)]
            (doseq [[path uri] (classpath-resources "oxygen/framework/")
                    :let [entry-path (str "zdl-lex-client/" path)]]
              (.. zip (putNextEntry (ZipEntry. entry-path)))
              (io/copy (io/input-stream uri) zip)
              (.. zip (closeEntry))))
          (catch Exception e (timbre/warn e))))
      (rio/piped-input-stream)
      (htstatus/ok)))

(def ring-handlers
  ["/oxygen"
   ["/updateSite.xml" {:get generate-update-descriptor}]
   ["/zdl-lex-plugin.zip" {:get download-plugin}]
   ["/zdl-lex-framework.zip" {:get download-framework}]])

(comment
  (-> (download-framework nil) :body slurp count))
