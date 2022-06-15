(ns zdl.lex.client.links
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [seesaw.bind :as uib]
   [seesaw.border :refer [empty-border line-border]]
   [seesaw.core :as ui]
   [seesaw.mig :as mig]
   [zdl.lex.article :as article]
   [zdl.lex.bus :as bus]
   [zdl.lex.client.font :as client.font]
   [zdl.lex.client.http :as client.http]
   [zdl.lex.client.icon :as client.icon]
   [zdl.lex.url :as lexurl]))

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
  (if-not doc
    (do
      (reset! current-links [])
      (reset! missing-anchors []))
    (when-let [article (article/extract-article doc)]
      (let [id      (lexurl/url->id url)
            anchors (seq (:anchors article))
            links   (seq (map :anchor (:links article)))]
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
              (reset! missing-anchors []))))))))

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

(defn render-missing-anchors
  [missing-anchors]
  (str "Fehlende Verweisziele: "
       (str/join ", " (map #(str "„" % "”") missing-anchors))))

(def link-list
  (ui/listbox
   :model    []
   :listen   [:selection #(some-> % ui/selection open-link)]
   :renderer (proxy [javax.swing.DefaultListCellRenderer] []
               (getListCellRendererComponent
                 [component value index selected? focus?]
                 (render-link value)))))

(def pane
  (ui/border-panel
   :north missing-anchors-label
   :center (ui/scrollable link-list)))

(defmethod ig/init-key ::events
  [_ _]
  [(bus/listen #{:editor-opened :editor-closed :editor-saved :editor-activated}
               update-links)
   (uib/bind current-links (uib/property link-list :model))
   (uib/bind missing-anchors
             (uib/transform seq)
             (uib/property missing-anchors-label :visible?))
   (uib/bind missing-anchors
             (uib/transform render-missing-anchors)
             (uib/property missing-anchors-label :text))])

(defmethod ig/halt-key! ::events
  [_ callbacks]
  (doseq [callback callbacks] (callback)))
