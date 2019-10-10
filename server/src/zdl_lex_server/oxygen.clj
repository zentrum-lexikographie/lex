(ns zdl-lex-server.oxygen
  (:require [ring.util.http-response :as htstatus]
            [clojure.java.io :as io]
            [zdl-lex-common.xml :as xml]))

(defn generate-update-descriptor [_]
  (let [{:keys [version]} (-> "version.edn" io/resource slurp read-string)
        descriptor (-> "oxygen/updateSite.xml" io/resource xml/->dom)
        elements-by-name #(-> (.getElementsByTagName descriptor %) xml/->seq)]
    (doseq [xt-version (elements-by-name "xt:version")]
      (.. xt-version (setTextContent version)))
    (htstatus/ok (xml/serialize descriptor))))

(defn download-plugin [_]
  (htstatus/ok))

(defn download-framework [_]
  (htstatus/ok))

(def ring-handlers
  ["/oxygen"
   ["/updateSite.xml" {:get generate-update-descriptor}]
   ["/zdl-lex-plugin.zip" {:get download-plugin}]
   ["/zdl-lex-framework.zip" {:get download-framework}]])
