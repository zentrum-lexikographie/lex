(ns user
  (:require
   [integrant.repl :refer [go halt reset reset-all]]
   [zdl.lex.client :as client]
   [zdl.lex.dev :as dev]
   [zdl.lex.fixtures :as fixtures]
   [zdl.lex.oxygen.url-handler :as url-handler]
   [zdl.lex.server :as server]))

(url-handler/install-stream-handler!)

(def config
  (merge
   dev/config
   fixtures/config
   client/config
   server/config-without-tasks))

(integrant.repl/set-prep!
 (fn []
   (fixtures/start-backends!)
   (fixtures/wait-for-backends!)
   config))

(comment
  (go)
  (halt)
  (reset)
  (reset-all))
