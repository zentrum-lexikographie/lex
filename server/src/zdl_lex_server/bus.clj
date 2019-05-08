(ns zdl-lex-server.bus
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [taoensso.timbre :as timbre]))

(def git-changes (async/chan))

(def git-changes-mult (async/mult git-changes))

(defstate git-changes-logger
  :start (let [changes-ch (async/tap git-changes-mult (async/chan))]
           (async/go-loop []
             (when-let [changes (async/<! changes-ch)]
               (timbre/info changes)
               (recur)))
           changes-ch)
  :stop (do
          (async/untap git-changes-mult git-changes-logger)
          (async/close! git-changes-logger)))
