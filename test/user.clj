(ns user
  (:require
   [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
   [integrant.core :as ig]
   [integrant.repl :refer [go halt reset reset-all]]
   [zdl.lex.client.io :refer [lexurl-handler]]
   [zdl.lex.env]
   [zdl.lex.url :as lexurl]
   [zdl.lex.util :refer [install-uncaught-exception-handler!]]))

(set-refresh-dirs "src" #_"test")

(install-uncaught-exception-handler!)

(try
  (lexurl/install-stream-handler! lexurl-handler)
  (catch Throwable _))

(require 'zdl.lex.client.dev)

(defn prep
  []
  (->
   zdl.lex.env/config
   (assoc :zdl.lex.client.dev/testbed {})
   (dissoc :zdl.lex.client.repl/server)))

(ig/load-namespaces zdl.lex.env/config)
(integrant.repl/set-prep! prep)

(comment
  (keys (prep))
  (go)
  (halt)
  (reset)
  (reset-all))


