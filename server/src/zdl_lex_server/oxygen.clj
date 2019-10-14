(ns zdl-lex-server.oxygen
  (:require [ring.util.http-response :as htstatus]
            [cpath-clj.core :as cp]
            [clojure.java.io :as io]
            [ring.util.io :as rio]
            [zdl-lex-common.xml :as xml]))

(defn generate-update-descriptor [_]
  (let [{:keys [version]} (-> "version.edn" io/resource slurp read-string)
        descriptor (-> "oxygen/updateSite.xml" io/resource xml/->dom)
        elements-by-name #(-> (.getElementsByTagName descriptor %) xml/->seq)]
    (doseq [xt-version (elements-by-name "xt:version")]
      (.. xt-version (setTextContent version)))
    (htstatus/ok (xml/serialize descriptor))))

(defn download-plugin [_]
  (-> (fn [stream] (spit stream (str (cp/resources "oxygen/plugin/lib/"))))
      (rio/piped-input-stream)
      (htstatus/ok)))

(defn download-framework [_]
  (-> (fn [stream] (spit stream (str (cp/resources "oxygen/framework/"))))
      (rio/piped-input-stream)
      (htstatus/ok)))

(def ring-handlers
  ["/oxygen"
   ["/updateSite.xml" {:get generate-update-descriptor}]
   ["/zdl-lex-plugin.zip" {:get download-plugin}]
   ["/zdl-lex-framework.zip" {:get download-framework}]])
