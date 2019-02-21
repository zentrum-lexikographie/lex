(ns dwdsox.env
  (:require [clojure.java.io :as io]
            [aero.core :refer (read-config)]
            [taoensso.timbre :as timbre]))

(def config (read-config (io/resource "dwdsox/config.edn")))

(def xml-db-type :basex)

(def xml-db-url (get-in config [xml-db-type :url]))

(timbre/handle-uncaught-jvm-exceptions!)
(timbre/merge-config! (get config :log))
