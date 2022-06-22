(ns zdl.lex.fixture.index
  (:require
   [clojure.test :refer [join-fixtures]]
   [zdl.lex.fixture.system :as fixture.system]
   [zdl.lex.server.git :as server.git]
   [zdl.lex.server.issue :as server.issue]
   [zdl.lex.server.solr.client :as solr.client]))

(defn articles
  [f]
  (let [system zdl.lex.fixture.system/*system*]
    (solr.client/clear! "article")
    (server.git/refresh! (get-in system [:zdl.lex.server.git/repo :dir]))
    (solr.client/optimize!))
  (f))

(defn issues
  [f]
  (let [system  zdl.lex.fixture.system/*system*]
    (solr.client/clear! "issue")
    (server.issue/sync! (get-in system [:zdl.lex.server.issue/db]))
    (solr.client/optimize!))
  (f))

(def all
  (join-fixtures [articles issues]))

