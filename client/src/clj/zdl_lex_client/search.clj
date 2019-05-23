(ns zdl-lex-client.search
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.border :refer [line-border]]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.results :as results]
            [zdl-lex-client.workspace :as workspace])
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
                                     :query {"q" (:query q) "limit" "100"}}))))
                      (merge q)
                      (async/>! results/renderer))
                 (catch Exception e (timbre/warn e)))
               (recur)))
           ch)
  :stop (async/close! requests))

(defn new-query [q]
  (async/>!! requests {:query q :id (str (UUID/randomUUID))}))

(defonce query (atom ""))

(declare input)

(def action
  (ui/action
   :icon icon/gmd-search
   :name "Suchen"
   :handler (fn [_] (.selectAll input) (new-query @query))))

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

        border-color (cond
                       (= "Red-f" status) :green
                       (= "Red-2" status) :yellow
                       (= "Artikelrumpf" status) :lightgrey
                       (.startsWith status "Lex-") :orange
                       (.endsWith status "-zurückgewiesen") :red
                       :else :white)
        border [5 (line-border :color border-color :right 10) 5]]

    (ui/config! this :text html :border border)))

(defn form-suggestions [q]
  (http/get-edn #(merge % {:path "/articles/forms/suggestions" :query {"q" q}})))

(def input
  (let [input (ui/text :columns 40 :action action)]
    (uib/bind input query)
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
        (async/>!! workspace/article-opener hint)))
    input))

(comment
  (form-suggestions "ab"))

