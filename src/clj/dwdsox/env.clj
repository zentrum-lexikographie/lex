(ns dwdsox.env
  (:require [clojure.java.io :as io]
            [aero.core :refer (read-config)]))

(def config (read-config (io/resource "dwdsox/config.edn")))

(def xml-db-type :basex)

(def xml-db-url (get-in config [xml-db-type :url]))
