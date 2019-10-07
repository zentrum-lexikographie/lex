(ns zdl-lex-corpus.dev
  (:require [zdl-lex-common.log :as log]
            [zdl-lex-corpus.toc :refer [corpora]]
            [clojure.core.async :as a]
            [taoensso.timbre :as timbre])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

(log/configure)

(comment
  (let [corpora (corpora)]
    (a/<!!
     (a/go
       (let [timeout (a/timeout 30000)]
         (->> (take 10 (vals corpora))
              (map (partial ddc/cmd->corpus "info" timeout))
              (a/merge)
              (a/into [])
              (a/<!)
              (remove nil?)
              (map :name)))))))
