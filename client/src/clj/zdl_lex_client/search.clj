(ns zdl-lex-client.search
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [lucene-query.core :as lucene]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.border :refer [line-border]]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.results :as results]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.article :as article])
  (:import com.jidesoft.hints.AbstractListIntelliHints
           java.util.UUID))

(defstate requests
  :start (let [ch (async/chan (async/sliding-buffer 3))]
           (async/go-loop []
             (when-let [q (async/<! ch)]
               (try
                 (->> (async/<!
                       (async/thread
                         (http/get-edn
                          #(merge % {:path "/articles/search"
                                     :query {"q" (:query q)
                                             "limit" "1000"}}))))
                      (merge q)
                      (async/>! results/renderer))
                 (catch Exception e (timbre/warn e)))
               (recur)))
           ch)
  :stop (async/close! requests))

(defn new-query [q]
  (async/>!! requests {:query q :id (str (UUID/randomUUID))}))

(def ^:private field-name-mapping
  {"def" "definitions"
   "form" "forms"
   "datum" "last-modified"
   "klasse" "pos"
   "bedeutung" "senses"
   "quelle" "sources"
   "status" "status"
   "tranche" "tranche"
   "typ" "type"})

(defn- expand-date [v]
  (-> v
      (str/replace #"^(\d{4}-\d{2})$" "$1-01")
      (str/replace #"^(\d{4}-\d{2}-\d{2})$" "$1T00\\:00\\:00Z")))

(def ^:private translate-query
  (comp
   lucene/ast->str
   (fn translate-node [node]
     (if (vector? node)
       (let [[type arg] node]
         (condp = type
           :field (let [[_ name] arg]
                    [:field [:term (or (field-name-mapping name) name)]])
           :term [:term (expand-date arg)]
           (vec (map translate-node node))))
       node))
   lucene/str->ast))

(comment
  (translate-query "datum:[1999-01 TO 2018-01-01}"))

(defn- validate-query [q]
  (try (translate-query q) true (catch Throwable t false)))

(defonce query (atom ""))

(defonce query-valid? (atom true))

(uib/bind query
          (uib/transform #(try (translate-query %) true (catch Throwable t false)))
          query-valid?)

(declare input)

(def action
  (ui/action
   :icon icon/gmd-search
   :name "Suchen"
   :handler (fn [_]
              (try
                (-> @query translate-query new-query)
                (.selectAll input)
                (catch Throwable t)))))

(defn- render-suggestion [this {:keys [value]}]
  (let [{:keys [suggestion pos type definitions status id last-modified]} value

        suggestion (-> suggestion
                       (str/replace "<b>" "<u>")
                       (str/replace "</b>" "</u>"))
        suggestion (str "<b>" suggestion "</b>")

        pos (-> pos first (or "?"))

        source (re-find #"^[^/]+" id)
        source (str "<font color=\"blue\">[" source "]</font>")

        title (str/join " " (remove nil? [suggestion pos source]))

        subtitle (str/join ", " (remove nil? [type status last-modified]))

        definition (-> definitions first (or "ohne Def."))
        definition-length (count definition)
        definition (subs definition 0 (min 40 definition-length))
        definition (str definition (if (< 40 definition-length) "…" ""))
        definition (str "<i>" definition "</i>")

        html (str/join "<br>" (remove nil? [title subtitle definition]))
        html (str "<html>" html "</html>")

        border-color (article/status->color value)
        border [5 (line-border :color border-color :right 10) 5]]

    (ui/config! this :text html :border border)))

(defn form-suggestions [q]
  (http/get-edn #(merge % {:path "/articles/forms/suggestions" :query {"q" q}})))

(def input
  (let [input (ui/text :columns 40 :action action)]
    (uib/bind input query)
    (uib/bind query-valid?
              (uib/transform #(if % :black :red))
              (uib/property input :foreground))
    (proxy [AbstractListIntelliHints] [input]
      (createList []
        (let [list (proxy-super createList)]
          (ui/config! list :background :white :renderer render-suggestion)))
      (updateHints [ctx]
        (let [q (str ctx)
              suggestions? (< 1 (count q))
              suggestions (if suggestions? (-> q form-suggestions :result))
              suggestions (or suggestions [])]
          (proxy-super setListData (into-array Object suggestions))
          (-> suggestions empty? not)))
      (acceptHint [hint]
        (proxy-super acceptHint (-> hint :suggestion (str/replace #"</?b>" "")))
        (workspace/open-article hint)))
    input))

(comment
  (form-suggestions "ab"))

