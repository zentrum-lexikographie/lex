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
  (dissoc (merge client/config
                 server/config
                 dev/config)
          :zdl.lex.metrics/reporter
          :zdl.lex.server.schedule/tasks))

(integrant.repl/set-prep! (constantly config))

(defn backends!
  []
  (fixtures/start-backends!)
  (fixtures/wait-for-backends!))

(def init-with-test-data!
  fixtures/init-with-test-data!)

(comment
  (go)
  (halt)
  (reset)
  (reset-all))
