(ns zdl.lex.server.graph
  (:require [clojure.test :refer :all]
            [clojurewerkz.ogre.core :as g]
            [mount.core :as mount :refer [defstate]]
            [zdl.lex.data :as data]
            [zdl.lex.fs :refer [delete! path]])
  (:import org.apache.commons.configuration.MapConfiguration
           org.apache.tinkerpop.gremlin.structure.util.GraphFactory))

(def db-dir
  (delay (data/dir "janus-db")))

(def index-dir
  (delay (data/dir "janus-index")))

(defstate graph
  :start (->>
          (MapConfiguration.
           {"gremlin.graph" "org.janusgraph.core.JanusGraphFactory"
            "storage.backend" "berkeleyje"
            "storage.directory" (path @db-dir)
            "index.lex.backend" "lucene"
            "index.lex.directory" (path @index-dir)})
          (GraphFactory/open))
  :stop (.close graph))

(defn delete-graph!
  []
  (delete! @index-dir)
  (delete! @db-dir))

(defn graph-fixture
  [f]
  (try
    (mount/start #'graph)
    (f)
    (finally
      (mount/stop #'graph))))

(use-fixtures :once graph-fixture)

(deftest graph-setup
  (is (let [g (g/traversal graph)]
        (g/traverse g (g/addV "test") (g/next!))
        (g/traverse g (g/addV "test") (g/next!)))))
