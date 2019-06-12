(ns zdl-lex-client.search
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [lucene-query.core :as lucene]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.behave :refer [when-focused-select-all]]
            [seesaw.border :refer [line-border]]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.query :as query]
            [zdl-lex-client.results :as results]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.article :as article])
  (:import com.jidesoft.hints.AbstractListIntelliHints
           java.util.UUID))

(defn search-request [q]
  (http/get-edn #(merge % {:path "/articles/search" :query {"q" q "limit" "1000"}})))

(defstate requests
  :start (let [ch (async/chan (async/sliding-buffer 3))]
           (async/go-loop []
             (when-let [req (async/<! ch)]
               (try
                 (let [q (query/translate (req :query))]
                   (->> (async/thread (search-request q))
                        (async/<!)
                        (merge req)
                        (async/>! results/renderer)))
                 (catch Exception e (timbre/warn e)))
               (recur)))
           ch)
  :stop (async/close! requests))

(defn new-query [q]
  (async/>!! requests {:query q :id (str (UUID/randomUUID))}))

(defonce search-query (atom ""))

(defonce search-query-valid? (atom true))

(def action
  (ui/action
   :name "Suchen"
   :icon icon/gmd-search
   :handler (fn [_]
              (let [search-query @search-query]
                (when (query/valid? search-query)
                  (new-query search-query))))))

(def input (ui/text :columns 40 :action action))

(when-focused-select-all input)

(uib/bind search-query (uib/transform query/valid?) search-query-valid?)

(uib/bind input
          search-query)

(uib/bind search-query-valid?
          (uib/transform #(if % :black :red))
          (uib/property input :foreground))

(defn form-suggestions [q]
  (http/get-edn #(merge % {:path "/articles/forms/suggestions" :query {"q" q}})))

(defn- suggestion->html [{:keys [suggestion pos type definitions
                                 status id last-modified]}]
  (let [suggestion (-> suggestion
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
        html (str/join "<br>" (remove nil? [title subtitle definition]))]
    (str "<html>" html "</html>")))

(defn- suggestion->border [suggestion]
  [5 (line-border :color (article/status->color suggestion) :right 10) 5])

(defn with-suggestions [input]
  (proxy [AbstractListIntelliHints] [input]
    (createList []
      (ui/config! (proxy-super createList)
                  :background :white
                  :renderer (fn [this {:keys [value]}]
                              (ui/config! this
                                          :text (suggestion->html value)
                                          :border (suggestion->border value)))))
    (updateHints [ctx]
      (let [q (str ctx)
            suggestions? (< 1 (count q))
            suggestions (if suggestions? (-> q form-suggestions :result))
            suggestions (or suggestions [])]
        (proxy-super setListData (into-array Object suggestions))
        (-> suggestions empty? not)))
    (acceptHint [hint]
      (workspace/open-article hint)))
  input)

(with-suggestions input)

(comment
  (form-suggestions "ab"))

