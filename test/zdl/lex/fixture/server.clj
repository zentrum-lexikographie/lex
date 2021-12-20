(ns zdl.lex.fixture.server
  (:require [zdl.lex.server :as server]
            [zdl.lex.server.article :as article]))

(defn instance
  [f]
  (try
    (server/start)
    (f)
    (finally
      (server/stop))))
