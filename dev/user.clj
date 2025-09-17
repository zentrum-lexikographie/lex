(ns user
  (:require
   [clojure.tools.namespace.repl :as repl :refer [set-refresh-dirs]]
   [zdl.lex.dev :as dev]
   [zdl.lex.fixtures :as fixtures]
   [zdl.lex.oxygen.url-handler :as url-handler]
   [zdl.lex.server :as server]))

(set-refresh-dirs "dev" "src" "test")

(url-handler/install-stream-handler!)

(defn go
  []
  (server/start)
  (dev/show!))

(defn halt
  []
  (dev/dispose!)
  (server/stop))

(defn reset
  []
  (halt)
  (repl/refresh :after 'user/go))

(defn reset-all
  []
  (halt)
  (repl/refresh-all :after 'user/go))

(defn backends!
  []
  (fixtures/start-backends!)
  (fixtures/wait-for-backends!))

(def init-with-test-data!
  fixtures/init-with-test-data!)
