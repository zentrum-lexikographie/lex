(ns zdl.lex.client.repl
  (:require [integrant.core :as ig]
            [nrepl.server :as repl]
            [clojure.tools.logging :as log]))

(defmethod ig/init-key ::server
  [_ {:keys [port]}]
  (log/info (format "Starting REPL server @%s/tcp" port))
  (repl/start-server :port port))

(defmethod ig/halt-key! ::server
  [_ server]
  (repl/stop-server server))
