(ns zdl-lex-client.view.issue
  (:require [clojure.core.memoize :as memo]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.border :refer [empty-border line-border]]
            [seesaw.core :as ui]
            [seesaw.mig :as mig]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.font :as font]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as ws]
            [zdl-lex-common.article :as article])
  (:import java.net.URL))

(def visited-issues (atom #{}))

(defn open-issue [{:keys [id last-updated url]}]
  (swap! visited-issues conj [id last-updated])
  (ws/open-url ws/instance url))

(defn- issue->border [{:keys [active? severity]}]
  (let [color (if-not active? :lightgreen
                      (condp = severity
                        "feature" :grey
                        "trivial" :grey
                        "text"  :grey
                        "tweak" :grey
                        "minor" :yellow
                        "major" :orange
                        "crash" :red
                        "block" :red
                        :orange))]
    [5
     (line-border :color color :left 10)
     (line-border :thickness 5 :color :white)]))

(def ^:private date-time-formatter
  (java.time.format.DateTimeFormatter/ofPattern "dd.MM.yyyy, HH:mm"))

(defn render-issue
  [{:keys [active? last-updated resolution severity status summary visited?]
    :as issue}]
  (let [fg-color (if active? :black :lightgray)
        bg-color (if active? :snow :white)
        visited-color (if visited? :green fg-color)
        label (partial ui/label :foreground fg-color)
        text (partial label :font (font/derived :style :plain))
        last-updated (.. date-time-formatter (format last-updated))]
    (mig/mig-panel
     :cursor :hand
     :background bg-color
     :border (issue->border issue)
     :listen [:mouse-pressed (partial open-issue issue)]
     :items [[(label :icon icon/gmd-bug-report
                     :foreground visited-color
                     :text summary
                     :tip summary
                     :border [(empty-border :bottom 2)
                              (line-border :color fg-color :bottom 1)])
              "span 2, width ::(100% - 80), wrap"]
             [(label :text "Datum")] [(text :text last-updated) "wrap"]
             [(label :text "Severity")] [(text :text severity) "wrap"]
             [(label :text "Status")] [(text :text status) "wrap"]
             [(label :text "Resolution")] [(text :text resolution)]])))

(def get-issues
  (memo/ttl http/get-issues :ttl/threshold (* 15 60 1000)))

(defn- parse-update-ts
  [^String ts]
  (->> (.. java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME (parse ts))
       (java.time.OffsetDateTime/from)))

(defn prepare-issues [[[_ url] visited?]]
  (let [visited? (or visited? @visited-issues)]
    (->> (ws/xml-document ws/instance url)
         (article/doc->articles)
         (map article/excerpt)
         (mapcat :forms)
         (mapcat get-issues)
         (map (fn [{:keys [id last-updated status url] :as issue}]
                (let [last-updated (parse-update-ts last-updated)]
                  (assoc issue
                         :url (URL. url)
                         :last-updated last-updated
                         :active? (not (#{"closed" "resolved"} status))
                         :visited? (visited? [id last-updated])))))
         (sort-by #(vector (:active? %) (:last-updated %)) #(compare %2 %1)))))

(def issue-list
  (ui/listbox
     :model []
     :listen [:selection #(some-> % ui/selection open-issue)]
     :renderer (proxy [javax.swing.DefaultListCellRenderer] []
                 (getListCellRendererComponent
                   [component value index selected? focus?]
                   (render-issue value)))))

(defstate issue-update
  :start (uib/bind (uib/funnel (bus/bind [:editor-activated :editor-saved])
                               visited-issues)
                   (uib/transform prepare-issues)
                   (uib/property issue-list :model))
  :stop (issue-update))

(def panel
  (ui/scrollable issue-list))

(comment
  (let [sample {:category "MWA-Link",
                :last-updated (parse-update-ts "2019-08-21T12:02:10+02:00"),
                :attachments 1,
                :resolution "open",
                :lemma "schwarz",
                :summary "schwarz -- MWA-Link, gemeldet von Reckenthäler",
                :reporter "dwdsweb",
                :status "assigned",
                :id 39947,
                :notes 0,
                :severity "minor",
                :url "http://odo.dwds.de/mantis/view.php?id=39947",
                :handler "herold"
                :active? true
                :visited? true}]
    (->> (render-issue sample) (ui/frame :content) ui/pack! ui/show!)))
