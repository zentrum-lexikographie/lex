(ns zdl.lex.fixture.server
  (:require [zdl.lex.server :as server]))

(defn instance
  [f]
  (try
    (server/start)
    (f)
    (finally
      (server/stop))))
