(ns zdl.lex.client.repl
  (:require
   [integrant.core :as ig]
   [nrepl.server :as repl]))

(defmethod ig/init-key ::server
  [_ {:keys [port]}]
  (repl/start-server :port port))

(defmethod ig/halt-key! ::server
  [_ server]
  (repl/stop-server server))
