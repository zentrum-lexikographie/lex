(ns zdl.lex.client.issue
  (:require
   [clojure.tools.logging :as log]
   [lambdaisland.uri :as uri]
   [mount.core :refer [defstate]]
   [seesaw.bind :as uib]
   [seesaw.border :refer [empty-border line-border]]
   [seesaw.core :as ui]
   [seesaw.mig :as mig]
   [zdl.lex.article :as article]
   [zdl.lex.bus :as bus]
   [zdl.lex.client.font :as client.font]
   [zdl.lex.client.http :as client.http]
   [zdl.lex.client.icon :as client.icon])
  (:import
   (java.net URL)))

(def issue-cache
  (atom {}))

(def current-issues
  (atom []))

(defn get-issues
  [url doc]
  (when-let [articles (article/extract-articles doc :errors? false)]
    (when-let [forms (seq (mapcat :forms articles))]
      (try
        (let [req {:url          "mantis"
                   :query-params {:q forms}}
              resp (client.http/request req)
              issues (get-in resp [:body :result])]
          (swap! issue-cache assoc url issues)
          (reset! current-issues issues))
        (catch Throwable t
          (log/warnf t "Error while retrieving issues for %s" forms))))))

(defn update-issues
  [topic {:keys [url doc]}]
  (condp = topic
    :editor-content-changed (get-issues url doc)
    :editor-activated       (reset! current-issues (get @issue-cache url []))
    :editor-closed          (swap! issue-cache dissoc url)))

(defstate issue-update
  :start (bus/listen #{:editor-content-changed :editor-closed :editor-activated}
                     update-issues)
  :stop (issue-update))

(def visited-issues
  (atom #{}))

(defn open-issue
  [{:keys [id last-updated url]}]
  (swap! visited-issues conj [id last-updated])
  (bus/publish! #{:open-url} {:url url}))

(defn- issue->border
  [{:keys [active? severity]}]
  (let [color (if-not active?
                :lightgreen
                (condp = severity
                  "feature" :grey
                  "trivial" :grey
                  "text"    :grey
                  "tweak"   :grey
                  "minor"   :yellow
                  "major"   :orange
                  "crash"   :red
                  "block"   :red
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
        visited-color (if visited? :grey fg-color)
        label (partial ui/label :foreground fg-color)
        text (partial label :font (client.font/derived :style :plain))
        last-updated (.. date-time-formatter (format last-updated))]
    (mig/mig-panel
     :cursor :hand
     :background bg-color
     :border (issue->border issue)
     :listen [:mouse-pressed (partial open-issue issue)]
     :items [[(label :icon client.icon/gmd-bug-report
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

(defn- parse-update-ts
  [^String ts]
  (->> (.. java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME (parse ts))
       (java.time.OffsetDateTime/from)))

(def mantis-issue-view
  (uri/uri "https://mantis.dwds.de/mantis/view.php"))

(defn prepare-issue
  [visited? {:keys [id last-updated status] :as issue}]
  (let [id           (:path (uri/uri id))
        last-updated (parse-update-ts last-updated)]
    (assoc issue
           :url (URL. (str (uri/assoc-query mantis-issue-view "id" id)))
           :last-updated last-updated
           :active? (not (#{"closed" "resolved"} status))
           :visited? (visited? [id last-updated]))))

(defn prepare-issues
  [_]
  (let [visited? @visited-issues
        issues   (map (partial prepare-issue visited?) @current-issues)]
    (sort-by (juxt :active? :last-updated) #(compare %2 %1) issues)))

(def issue-list
  (ui/listbox
     :model []
     :listen [:selection #(some-> % ui/selection open-issue)]
     :renderer (proxy [javax.swing.DefaultListCellRenderer] []
                 (getListCellRendererComponent
                   [component value index selected? focus?]
                   (render-issue value)))))

(defstate issue-renderer
  :start (uib/bind (uib/funnel current-issues visited-issues)
                   (uib/transform prepare-issues)
                   (uib/property issue-list :model))
  :stop (issue-renderer))


(def panel
  (ui/scrollable issue-list))
