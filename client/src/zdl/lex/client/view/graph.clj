(ns zdl.lex.client.view.graph
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [seesaw.core :as ui]
            [zdl.lex.client.bus :as bus])
  (:import com.google.common.base.Supplier
           edu.uci.ics.jung.algorithms.generators.random.ErdosRenyiGenerator
           [edu.uci.ics.jung.algorithms.layout FRLayout ISOMLayout SpringLayout]
           [edu.uci.ics.jung.graph DirectedSparseGraph DirectedSparseMultigraph UndirectedSparseGraph]
           edu.uci.ics.jung.graph.util.TestGraphs
           [edu.uci.ics.jung.visualization GraphZoomScrollPane Layer VisualizationViewer]
           edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse))

(def graph
  (atom nil))

(def panel
  (ui/border-panel :center (ui/label :id :graph-view)))

(defn graph->model
  [{:keys [nodes edges] :as g}]
  (reset! graph g)
  (let [model (DirectedSparseMultigraph.)]
    (doseq [node (keys nodes)]
      (.addVertex model node))
    (doseq [[id [src target]] (map-indexed list edges)]
      (.addEdge model id src target))
    model))

(defn render-graph!
  [g]
  (let [g           {:nodes (into {} (g :nodes))
                     :edges (vec (g :edges))}
        model       (graph->model g)
        layout      (ISOMLayout. model)
        graph-mouse (DefaultModalGraphMouse.)
        viewer      (doto (VisualizationViewer. layout)
                      (.setBackground java.awt.Color/WHITE)
                      (.setGraphMouse graph-mouse)
                      (.addKeyListener
                       (.getModeKeyListener graph-mouse)))
        scroll-pane (ui/config! (GraphZoomScrollPane. viewer) :id :graph-view)]
    (ui/invoke-soon
     (ui/replace! panel (ui/select panel [:#graph-view]) scroll-pane))))

#_(let [transformer (.. viewer getRenderContext getMultiLayerTransformer)]
    (.. transformer (getTransformer Layer/LAYOUT) (setToIdentity))
    (.. transformer (getTransformer Layer/VIEW) (setToIdentity)))

(defstate graph-renderer
  :start (bus/listen #{:graph} (fn [_ [id graph]] (render-graph! graph)))
  :stop (graph-renderer))

(comment
  @graph)
