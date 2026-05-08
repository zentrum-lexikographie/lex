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
  (reset-all)

  (do (require '[nextjournal.clerk :as clerk])
      (clerk/serve! {:browse? true :watch-paths ["notebooks"]}))
  (do (require '[nextjournal.clerk :as clerk])
      (clerk/build! {:paths   ["notebooks/trend_word_detection.clj"]
                     :package :single-file})))
