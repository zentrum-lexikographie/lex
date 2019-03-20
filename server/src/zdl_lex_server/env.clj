(ns zdl-lex-server.env
  (:require [taoensso.timbre :as timbre]
            [aero.core :refer [read-config]]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs])
  (:import [org.slf4j.bridge SLF4JBridgeHandler]))

(def config
  (let [config-resource (comp read-config io/resource)
        local-config (fs/file "zdl-lex-server.config.edn")]
    (merge
     (read-config (io/resource "config.edn"))
     (if (fs/readable? local-config) (read-config (.getPath local-config)) {}))))
