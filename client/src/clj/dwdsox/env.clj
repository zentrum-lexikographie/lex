(ns dwdsox.env
  (:require [clojure.java.io :as io]
            [aero.core :refer (read-config)]
            [taoensso.timbre :as timbre]))

(def config (read-config (io/resource "dwdsox/config.edn")))

(timbre/handle-uncaught-jvm-exceptions!)
(timbre/merge-config! (get config :log))
