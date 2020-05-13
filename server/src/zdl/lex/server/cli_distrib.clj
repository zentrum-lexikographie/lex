(ns zdl.lex.server.cli-distrib
  (:require [clojure.java.io :as io]
            [ring.util.http-response :as htstatus]
            [ring.util.io :as rio]))

(defn download-cli-jar
  [_]
  (->
   (fn [os]
     (with-open [is (-> "org.zdl.lex.cli.jar" io/resource io/input-stream)]
       (io/copy is os)))
   (rio/piped-input-stream)
   (htstatus/ok)))

(def ring-handlers
  ["/cli/org.zdl.lex.cli.jar" download-cli-jar])

(comment
  (-> (download-cli-jar nil) :body slurp count))


