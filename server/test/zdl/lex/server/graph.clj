(ns zdl.lex.server.graph
  (:require [clojure.test :refer :all]
            [clojurewerkz.ogre.core :as g]
            [mount.core :as mount :refer [defstate]]
            [zdl.lex.data :as data]
            [zdl.lex.fs :refer [delete! path]])
  (:import java.util.Map
           org.apache.commons.configuration.MapConfiguration
           org.apache.tinkerpop.gremlin.structure.util.GraphFactory
           org.janusgraph.core.JanusGraphTransaction
           org.janusgraph.graphdb.database.StandardJanusGraph))

(set! *warn-on-reflection* true)

(def db-dir
  (delay (data/dir "janus-db")))

(def index-dir
  (delay (data/dir "janus-index")))

(defstate graph
  :start (let [^Map config
               {"gremlin.graph" "org.janusgraph.core.JanusGraphFactory"
                "storage.backend" "berkeleyje"
                "storage.directory" (path @db-dir)
                "index.lex.backend" "lucene"
                "index.lex.directory" (path @index-dir)}]
           (GraphFactory/open (MapConfiguration. config)))
  :stop (.close ^StandardJanusGraph graph))

(defn delete-graph!
  []
  (delete! @index-dir)
  (delete! @db-dir))

(defn ^JanusGraphTransaction new-tx!
  [^StandardJanusGraph graph]
  (.newTransaction graph))

(defn commit-tx!
  [^JanusGraphTransaction tx]
  (.commit tx))

(defn rollback-tx!
  [^JanusGraphTransaction tx]
  (.rollback tx))

(defmacro with-tx
  [binding & body]
  `(let [tx# (new-tx! ~(second binding))
         ~(first binding) (g/traversal tx#)]
     (try
       (let [result# (do ~@body)]
         (commit-tx! tx#)
         result#)
       (catch Throwable t#
         (rollback-tx! tx#)
         (throw t#)))))

(defn graph-fixture
  [f]
  (try
    (mount/start #'graph)
    (f)
    (finally
      (mount/stop #'graph)
      (delete-graph!))))

(use-fixtures :once graph-fixture)

(deftest graph-setup
  (is
   (with-tx [g graph]
     (println (g/traverse g (g/V) (g/count) (g/next!)))
     (g/traverse g (g/addV "test") (g/next!))
     (g/traverse g (g/addV "test") (g/next!)))))
