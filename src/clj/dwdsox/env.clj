(ns dwdsox.env
  (:require [clojure.java.io :as io]
            [aero.core :refer (read-config)]))

(def config (read-config (io/resource "dwdsox/config.edn")))

