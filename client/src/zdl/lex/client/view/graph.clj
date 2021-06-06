(ns zdl.lex.client.view.graph
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [zdl.lex.client.bus :as bus])
  (:import com.google.common.base.Supplier
           edu.uci.ics.jung.algorithms.generators.random.ErdosRenyiGenerator
           [edu.uci.ics.jung.algorithms.layout FRLayout ISOMLayout]
           [edu.uci.ics.jung.graph DirectedSparseGraph DirectedSparseMultigraph UndirectedSparseGraph]
           edu.uci.ics.jung.graph.util.TestGraphs
           [edu.uci.ics.jung.visualization GraphZoomScrollPane Layer VisualizationViewer]
           edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse))

(defn supplier
  [f]
  (proxy [Supplier] []
    (get [] (f))))

(def graph
  (atom nil))

(def layout
  (FRLayout. (DirectedSparseMultigraph.)))

(def viewer
  (let [graph-mouse (DefaultModalGraphMouse.)]
    (doto (VisualizationViewer. layout)
      (.setBackground java.awt.Color/WHITE)
      (.setGraphMouse graph-mouse)
      (.addKeyListener (.getModeKeyListener graph-mouse)))))

(def panel
  (GraphZoomScrollPane. viewer))

(defn render-graph!
  [g]
  (let [{:keys [nodes edges] :as g} {:nodes (into {} (g :nodes))
                                     :edges (vec (g :edges))}
        model                       (DirectedSparseMultigraph.)]
    (reset! graph g)
    (doseq [node (keys nodes)]
      (.addVertex model node))
    (doseq [[id [src target]] (map-indexed list edges)]
      (.addEdge model id src target))
    (log/info model)
    (.setGraph layout model))
  (let [transformer (.. viewer getRenderContext getMultiLayerTransformer)]
    (.. transformer (getTransformer Layer/LAYOUT) (setToIdentity))
    (.. transformer (getTransformer Layer/VIEW) (setToIdentity))))

(defstate graph-renderer
  :start (bus/listen #{:graph} (fn [_ [id graph]] (render-graph! graph)))
  :stop (graph-renderer))

(comment
  @graph)
