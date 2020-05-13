(ns zdl.lex.server.graph
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojurewerkz.ogre.core :as g]
            [mount.core :as mount :refer [defstate]]
            [zdl.lex.data :as data]
            [zdl.lex.fs :refer [delete! path]]
            [zdl.lex.server.gen.article :refer [create-article-set-fixture]]
            [zdl.lex.server.git :as git]
            [zdl.lex.article :as article]
            [clojure.java.io :as io])
  (:import java.util.Map
           org.apache.commons.configuration.MapConfiguration
           org.apache.tinkerpop.gremlin.structure.util.GraphFactory
           org.apache.tinkerpop.gremlin.structure.Vertex
           org.apache.tinkerpop.gremlin.process.traversal.IO
           org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
           [org.janusgraph.core Cardinality EdgeLabel JanusGraphTransaction Multiplicity PropertyKey SchemaViolationException VertexLabel]
           [org.janusgraph.core.schema JanusGraphManagement JanusGraphManagement$IndexBuilder]
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
                "index.lex.directory" (path @index-dir)
                "schema.default" "none"}]
           (GraphFactory/open (MapConfiguration. config)))
  :stop (.close ^StandardJanusGraph graph))

(defn delete-graph!
  []
  (delete! @index-dir)
  (delete! @db-dir))

(defn enum->map
  [vals]
  (into {} (map #(vector (-> (.name ^Enum %) str/lower-case keyword) %)) vals))

(def cardinalities
  (enum->map (Cardinality/values)))

(def multiplicities
  (enum->map (Multiplicity/values)))

(defn array-type
  "Return a string representing the type of an array with dims
  dimentions and an element of type klass.
  For primitives, use a klass like Integer/TYPE
  Useful for type hints of the form: ^#=(array-type String) my-str-array"
  ([klass] (array-type klass 1))
  ([klass dims]
   (.getName (class
              (apply make-array
                     (if (symbol? klass) (eval klass) klass)
                     (repeat dims 0))))))

(defn ^PropertyKey prop!
  [^JanusGraphManagement m label {:keys [type cardinality]}]
  (.. m
      (makePropertyKey (name label))
      (dataType type)
      (cardinality ^Cardinality (cardinalities cardinality))
      (make)))

(defn ^EdgeLabel edge!
  [^JanusGraphManagement m props l {:keys [multiplicity properties]}]
  (let [e
        (.. m
            (makeEdgeLabel (name l))
            (multiplicity ^Multiplicity (multiplicities multiplicity))
            (make))
        ^#=(array-type PropertyKey) props
        (->> properties (map props) (into-array PropertyKey))]
    (.addProperties m e props)
    e))

(defn ^VertexLabel vertex!
  [^JanusGraphManagement m props l {:keys [properties]}]
  (let [v (.. m (makeVertexLabel (name l)) (make))
        ^#=(array-type PropertyKey) props
        (->> properties (map props) (into-array PropertyKey))]
    (.addProperties m v props)
    v))

(defn connection!
  [^JanusGraphManagement m edges verts {:keys [edge out in]}]
  (.addConnection m (edges edge) (verts out) (verts in)))

(defn index!
  [^JanusGraphManagement m props l {:keys [type keys unique? mixed]}]
  (let [index-name (str "by-" (name l))
        ^JanusGraphManagement$IndexBuilder builder
        (reduce #(.addKey ^JanusGraphManagement$IndexBuilder %1 %2)
                (.buildIndex m index-name type)
                (map #(.getPropertyKey m (name %)) keys))
        ^JanusGraphManagement$IndexBuilder builder
        (if unique? (.unique builder) builder)]
    [index-name
     (if mixed
       (.buildMixedIndex builder mixed)
       (.buildCompositeIndex builder))]))

(defn create-schema!
  [^StandardJanusGraph graph
   {:keys [properties vertices edges connections indexes]}]
  (let [^JanusGraphManagement m (.openManagement graph)
        schema? (seq (.getRelationTypes m EdgeLabel))]
    (when-not schema?
      (let [props (into {} (for [[l p] properties] [l (prop! m l p)]))
            edges (into {} (for [[l e] edges] [l (edge! m props l e)]))
            verts (into {} (for [[l v] vertices] [l (vertex! m props l v)]))
            _ (doseq [c connections] (connection! m edges verts c))
            indexes (into {} (for [[l i] indexes] (index! m props l i)))]
        (.commit m)))))

(defn ^JanusGraphTransaction new-tx
  [^StandardJanusGraph graph]
  (.newTransaction graph))

(defn commit-tx
  [^JanusGraphTransaction tx]
  (.commit tx))

(defn rollback-tx
  [^JanusGraphTransaction tx]
  (.rollback tx))

(defmacro with-tx
  [binding & body]
  `(let [tx# (new-tx ~(second binding))
         ~(first binding) (g/traversal tx#)]
     (try
       (let [result# (do ~@body)]
         (commit-tx tx#)
         result#)
       (catch Throwable t#
         (rollback-tx tx#)
         (throw t#)))))

(defn upsert-vertex
  [g label k v]
  (g/traverse
   g g/V (g/has label k v) (g/fold)
   (g/coalesce (g/__  (g/unfold))
               (g/__ (g/addV label) (g/property k v)))
   (g/next!)))

(defn upsert-edge
  [g source label target]
  (g/traverse
   g (g/V source)
   (g/coalesce (g/__ (g/outE) (g/filter (g/__ (g/inV) (g/has-id target))))
               (g/__ (g/addE label) (g/to target)))
   (g/next!)))

(defn write-graphml
  [^GraphTraversalSource g f]
  (-> g (.io (path f)) (.with IO/writer IO/graphml) (.write) (.iterate)))

(defn graph-fixture
  [f]
  (try
    (mount/start #'graph)
    (f)
    (finally
      (mount/stop #'graph)
      (delete-graph!))))

(defn graph-schema-fixture
  [f]
  (->>
   {:properties {:uri {:type String :cardinality :single}
                 :repr {:type String :cardinality :single}
                 :type {:type String :cardinality :single}}

    :vertices {:lexeme {:properties [:uri]}
               :form {:properties [:repr]}}

    :edges {:has-form {:multiplicity :multi :properties [:type]}}

    :connections [{:edge :has-form :out :lexeme :in :form}]

    :indexes {:uri {:type Vertex :keys [:uri] :unique? true}
              :repr {:type Vertex :keys [:repr] :unique? true}}}
   (create-schema! graph))
  (f))

(def article-set-fixture
  (create-article-set-fixture [10 100]))

(use-fixtures :once article-set-fixture graph-fixture graph-schema-fixture)

(deftest graph-setup
  (is
   (thrown?
    SchemaViolationException
    (with-tx [g graph]
      (g/traverse g (g/addV :lexeme) (g/property :uri "urn:test") (g/next!))
      (g/traverse g (g/addV :lexeme) (g/property :uri "urn:test") (g/next!)))))
  (is
   (with-tx [g graph]
     (upsert-vertex g :lexeme :uri "urn:test")
     (upsert-vertex g :lexeme :uri "urn:test")))
  (is
   (with-tx [g graph]
     (doseq [{:keys [id forms]} (article/articles @git/dir)]
       (let [lexeme (upsert-vertex g :lexeme :uri id)]
         (doseq [form forms]
           (let [form (upsert-vertex g :form :repr form)]
             (upsert-edge g lexeme :has-form form)))))
     (write-graphml g "test.graphml")
     true)))

