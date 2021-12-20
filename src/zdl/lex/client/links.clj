(ns zdl.lex.client.links
  (:require
   [clojure.tools.logging :as log]
   [mount.core :refer [defstate]]
   [seesaw.bind :as uib]
   [seesaw.border :refer [empty-border line-border]]
   [seesaw.core :as ui]
   [seesaw.mig :as mig]
   [zdl.lex.article :as article]
   [zdl.lex.bus :as bus]
   [zdl.lex.client.http :as client.http]
   [zdl.lex.client.font :as client.font]
   [zdl.lex.client.icon :as client.icon]
   [zdl.lex.url :as lexurl]
   [clojure.string :as str]))

(def current-links
  (atom []))

(def missing-anchors
  (atom []))

(defn prepare-links
  [anchors links result]
  (let [anchors (into #{} anchors)
        links   (into #{} links)
        result  (sort-by (comp article/collation-key :form) result)]
    (for [link result]
      (assoc link
             :incoming? (some anchors (:links link))
             :outgoing? (some links (:anchors link))))))

(defn update-links
  [_ {:keys [url doc]}]
  (when-let [articles (article/extract-articles doc :errors? false)]
    (let [id      (lexurl/url->id url)
          anchors (seq (mapcat :anchors articles))
          links   (seq (map :anchor (mapcat :links articles)))]
      (when (or anchors links)
        (try
          (let [req     {:url          "index/links"
                         :query-params (cond-> {}
                                         anchors (assoc :links anchors)
                                         links   (assoc :anchors links))}
                resp    (client.http/request req)
                result  (get-in resp [:body :result])
                result  (remove (comp #{id} :id) result)
                result  (prepare-links anchors links result)
                anchors (into #{} (mapcat :anchors) result)
                missing (into #{} (remove anchors) links)
                missing (sort-by article/collation-key missing)]
            (reset! current-links result)
            (reset! missing-anchors missing))
          (catch Throwable t
            (log/warnf t "Error while retrieving links for %s" url)
            (reset! current-links [])
            (reset! missing-anchors [])))))))

(defstate link-update
  :start (bus/listen #{:editor-content-changed :editor-activated} update-links)
  :stop (link-update))

(defn open-link
  [{:keys [id]}]
  (bus/publish! #{:open-article} {:id id}))

(defn render-link
  [{:keys [id status pos incoming? outgoing?] :as link}]
  (let [bidi? (and incoming? outgoing?)
        label ui/label
        text  (partial label :font (client.font/derived :style :plain))]
    (mig/mig-panel
     :cursor :hand
     :background :snow
     :border [5
              (line-border :color (article/status->color status) :left 10)
              (line-border :thickness 5 :color :white)]
     :listen [:mouse-pressed (partial open-link link)]
     :items [[(label :icon (cond
                             bidi?     client.icon/gmd-link-bidi
                             incoming? client.icon/gmd-link-incoming
                             outgoing? client.icon/gmd-link-outgoing)
                     :text (cond-> (link :form) pos (str " (" pos ")"))
                     :tip id
                     :border [(empty-border :bottom 2)
                              (line-border :color :black :bottom 1)])
              "span 2, width ::(100% - 80), wrap"]
             [(label :text "Status")] [(text :text status) "wrap"]
             [(label :text "Typ")] [(text :text (link :type)) "wrap"]
             [(label :text "Quelle")] [(text :text (link :source)) "wrap"]
             [(label :text "Zeitstempel")] [(text :text (link :last-modified))]])))

(def missing-anchors-label
  (ui/text :multi-line? true
           :editable? false
           :wrap-lines? true
           :text ""
           :foreground :snow
           :background :red
           :border (line-border :thickness 5 :color :red)
           :font (client.font/derived :style :bold)
           :visible? false))

(def link-list
  (ui/listbox
   :model []
   :listen [:selection #(some-> % ui/selection open-link)]
   :renderer (proxy [javax.swing.DefaultListCellRenderer] []
               (getListCellRendererComponent
                 [component value index selected? focus?]
                 (render-link value)))))

(defn render-missing-anchors
  [missing-anchors]
  (str "Fehlende Verweisziele: "
       (str/join ", " (map #(str "„" % "”") missing-anchors))))

(defstate link-renderer
  :start [(uib/bind current-links (uib/property link-list :model))
          (uib/bind missing-anchors
                    (uib/transform seq)
                    (uib/property missing-anchors-label :visible?))
          (uib/bind missing-anchors
                    (uib/transform render-missing-anchors)
                    (uib/property missing-anchors-label :text))]
  :stop (doseq [renderer link-renderer] (renderer)))

(def pane
  (ui/border-panel
   :north missing-anchors-label
   :center (ui/scrollable link-list)))
