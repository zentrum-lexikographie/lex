(ns zdl.lex.fixture.system
  (:require [integrant.core :as ig]
            [zdl.lex.env :as env]
            [zdl.lex.fs :as fs]))

(def ^:dynamic *system*)

(def test-data
  (fs/ensure-dirs "test-data"))

(def git-dir
  (get-in env/config [:zdl.lex.server.git/repo :dir]))

(defn instance
  ([f]
   (instance env/server-config-keys f))
  ([config-keys f]
   (let [system (ig/init env/config config-keys)]
     (try
       (binding [*system* system]
         (f))
       (finally
         (ig/halt! system))))))
