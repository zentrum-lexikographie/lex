(ns zdl.lex.corpus.dev
  (:require [zdl.lex.corpus.ddc :as ddc]
            [zdl.lex.corpus.toc :refer [corpora]]
            [clojure.core.async :as a])
  (:import org.slf4j.bridge.SLF4JBridgeHandler))

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
