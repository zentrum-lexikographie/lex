(ns user
  (:require
   [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
   [integrant.core :as ig]
   [integrant.repl :refer [go halt reset reset-all]]
   [zdl.lex.client.io :refer [lexurl-handler]]
   [zdl.lex.env :as env]
   [zdl.lex.url :as lexurl]
   [zdl.lex.util :refer [install-uncaught-exception-handler!]]))

(set-refresh-dirs "src" "test")

(install-uncaught-exception-handler!)

(try
  (lexurl/install-stream-handler! lexurl-handler)
  (catch Throwable _))

(def prep
  (constantly (select-keys env/config env/dev-config-keys)))

(ig/load-namespaces env/config)
(integrant.repl/set-prep! prep)

(comment
  (sort (keys (prep)))
  (go)
  (halt)
  (reset)
  (reset-all))


