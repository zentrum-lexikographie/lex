(ns zdl.lex.fixture.solr
  (:require [clojure.test :refer [join-fixtures]]
            [zdl.lex.server.git :as server.git]
            [zdl.lex.server.solr.client :as solr.client]
            [zdl.lex.server.issue :as server.issue]))

(defn articles
  [f]
  (solr.client/clear! "article")
  (server.git/refresh!)
  (solr.client/optimize!)
  (f))

(defn issues
  [f]
  (solr.client/clear! "issue")
  (server.issue/sync!)
  (solr.client/optimize!)
  (f))

(def index
  (join-fixtures [articles issues]))

