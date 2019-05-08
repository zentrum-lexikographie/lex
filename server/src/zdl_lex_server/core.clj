(ns zdl-lex-server.core
  (:gen-class)
  (:require [mount.core :as mount]
            [zdl-lex-server.env :as env]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.http :as http]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]))

(defn -main []
  (mount/start #'env/logging
               #'store/git-clone
               #'git/changes
               #'solr/index-changes
               #'http/server)
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. (fn [] (mount/stop) (shutdown-agents))))
  (.join http/server))
