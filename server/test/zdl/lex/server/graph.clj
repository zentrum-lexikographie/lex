(ns zdl.lex.server.graph
  (:require  [clojure.test :refer :all]
             [zdl.lex.data :as data]
             [zdl.lex.fs :refer [path]]
             [clojurewerkz.ogre.core :as g])
  (:import java.util.Properties
           org.apache.commons.configuration.MapConfiguration
           org.apache.tinkerpop.gremlin.structure.util.GraphFactory))

(def config
  (delay
    (MapConfiguration.
     {"gremlin.graph" "org.janusgraph.core.JanusGraphFactory"
      "storage.backend" "berkeleyje"
      "storage.directory" (path (data/dir "janus-db"))
      "index.lex.backend" "lucene"
      "index.lex.directory" (path (data/dir "janus-index"))})))

(comment
  (with-open [g (GraphFactory/open @config)]
    (.. g (tx) (open))
    (try
      (let [g (g/traversal g)]
        (g/traverse g g/V (g/addV "test") (g/next!))
        (g/traverse g g/V (g/addV "test") (g/next!))
        (g/traverse g g/V (g/count) (g/next!)))
      (finally (.. g (tx) (commit))))))
