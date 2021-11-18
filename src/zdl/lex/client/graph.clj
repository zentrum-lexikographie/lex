(ns zdl.lex.client.graph
  (:require [lambdaisland.uri :as uri]
            [mount.core :refer [defstate]]
            [zdl.lex.bus :as bus]
            [zdl.lex.client.http :as client.http]
            [zdl.lex.url :as lexurl]
            [seesaw.core :as ui]
            [seesaw.border :as ui.border]
            [seesaw.tree :as ui.tree]
            [clojure.string :as str]
            [zdl.lex.client.icon :as client.icon]
            [clj-http.client :as http]))

(def graph
  (atom nil))

(defn get-graph
  [id]
  (get (client.http/request {:url (uri/join "graph/" id)}) :body))

(defn update-graph!
  [_ {:keys [url]}]
  (let [id (lexurl/url->id url)]
    (reset! graph (get-graph id))))

(defstate graph-update
  :start (bus/listen #{:editor-activated :editor-saved} update-graph!)
  :stop (graph-update))

(def pane
  (ui/card-panel))

(defn article->model
  [article]
  (assoc article :type :article :children []))

(defn issue->model
  [issue]
  (assoc issue :type :issue))

(defn issues->model
  [issues article]
  (if-let [issues (seq (concat (issues (article :form)) (issues (article :anchor))))]
    (let [issues (map issue->model issues)]
      (update article :children conj {:type :issues :children issues}))
    article))

(defn links->model
  [article links link-type]
  (cond-> article
    (seq links) (update :children conj {:type link-type :children links})))

(defn graph->tree-model
  [{:keys [article links issues]}]
  (let [issues->model (partial issues->model (group-by :form issues))
        link->model   (comp issues->model article->model)
        incoming      (map link->model (filter :incoming links))
        outgoing      (map link->model (remove :incoming links))]
    (->
     article
     (article->model)
     (links->model outgoing :outgoing)
     (links->model incoming :incoming)
     (issues->model))))

(defn article->str
  [{:keys [id anchor source pos incoming]}]
  (str (if incoming id (or anchor id)) " (" source ", " (or pos "-") ")"))

(defn links->str
  [{:keys [children]} incoming]
  (str (if incoming "Eingehende" "Ausgehende") " Verweise (" (count children) ")"))

(defn issues->str
  [{:keys [children]}]
  (str "Mantis-Tickets (" (count children) ")"))

(defn issue->str
  [_]
  "Mantis-Ticket")

(defn render-tree-node
  [this {{:keys [type] :as node} :value}]
  (ui/config! this
              :text (condp = type
                      :article (article->str node)
                      :incoming (links->str node true)
                      :outgoing (links->str node false)
                      :issues  (issues->str node)
                      :issue   (issue->str node))
              :icon client.icon/gmd-result))

(defn tree-model-paths
  ([root]
   (tree-model-paths [] root))
  ([path node]
   (let [path (conj path node)]
     (lazy-seq
      (cons path (mapcat #(tree-model-paths path %) (node :children)))))))

(defn expand-paths
  [tree]
  (loop [row 0 num-rows (.getRowCount tree)]
    (when (< row num-rows)
      (when (<= 0 (rand))
        (.expandRow tree row))
      (recur (inc row) (.getRowCount tree))))
  tree)

(defn tree-model->pane
  [root]
  (ui/scrollable
    (ui/tree
     :id :graph
     :model (ui.tree/simple-tree-model (comp seq :children) :children root)
     :renderer render-tree-node
     :editable? false
     :toggle-click-count 1
     :scrolls-on-expand? true
     :border 5)))

(defn dev-frame
  [pane]
  (->
   (ui/frame :title "zdl-lex-client/graph-dev"
             :content pane
             :minimum-size [640 :by 640])
   (ui/pack!)
   (ui/show!)
   (ui/invoke-now)))

(comment
  (let [id    "Neuartikel/zwischen_allen_Stuhlen_stehen-E_5826791.xml"
        graph (get-graph id)
        model (graph->tree-model graph)
        pane  (tree-model->pane model)]
    (dev-frame pane)
    (ui/invoke-soon (expand-paths (ui/select pane [:#graph]))))
  (->
   (get-graph "Neuartikel/zwischen_allen_Stuhlen_stehen-E_5826791.xml")
   #_(graph->tree-model))
  (bus/publish!
   #{:editor-activated}
   {:url (lexurl/id->url "Neuartikel/Test-E_7647544.xml")}))
