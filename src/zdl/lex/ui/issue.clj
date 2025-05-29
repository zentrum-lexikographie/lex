(ns zdl.lex.ui.issue
  (:require
   [lambdaisland.uri :as uri]
   [seesaw.bind :as uib]
   [seesaw.border :refer [empty-border line-border]]
   [seesaw.core :as ui]
   [seesaw.mig :as mig]
   [zdl.lex.oxygen.workspace :as workspace]
   [zdl.lex.ui.util :as util]
   [zdl.lex.client :as client])
  (:import
   (java.net URL)))

(def visited
  (atom #{}))

(defn open-issue
  [{:keys [id updated url]}]
  (swap! visited conj [id updated])
  (workspace/open-url url))

(defn- issue->border-color
  [{:keys [active? severity]}]
  (if-not active?
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
      :orange)))

(defn- issue->border
  [issue]
  [5
   (line-border :color (issue->border-color issue) :left 10)
   (line-border :thickness 5 :color :white)])

(def ^:private date-time-formatter
  (java.time.format.DateTimeFormatter/ofPattern "dd.MM.yyyy, HH:mm"))

(defn render-issue
  [{:keys [active? updated resolution severity status summary visited?]
    :as   issue}]
  (let [fg-color      (if active? :black :lightgray)
        bg-color      (if active? :snow :white)
        visited-color (if visited? :grey fg-color)
        label         (partial ui/label :foreground fg-color)
        text          (partial label :font (util/derived-font :style :plain))
        updated       (.. date-time-formatter (format updated))]
    (mig/mig-panel
     :cursor :hand
     :background bg-color
     :border (issue->border issue)
     :listen [:mouse-pressed (partial open-issue issue)]
     :items [[(label :icon (util/icon :bug-report)
                     :foreground visited-color
                     :text summary
                     :tip summary
                     :border [(empty-border :bottom 2)
                              (line-border :color fg-color :bottom 1)])
              "span 2, width ::(100% - 80), wrap"]
             [(label :text "Datum")] [(text :text updated) "wrap"]
             [(label :text "Severity")] [(text :text severity) "wrap"]
             [(label :text "Status")] [(text :text status) "wrap"]
             [(label :text "Resolution")] [(text :text resolution)]])))

(def issue-list
  (ui/listbox
   :model []
   :listen [:selection #(some-> % ui/selection open-issue)]
   :renderer (proxy [javax.swing.DefaultListCellRenderer] []
               (getListCellRendererComponent
                 [component value index selected? focus?]
                 (render-issue value)))))

(def panel
  (ui/scrollable issue-list))

(defn parse-update-ts
  [^String ts]
  (->> (.. java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME (parse ts))
       (java.time.OffsetDateTime/from)))

(def mantis-issue-view
  (uri/uri "https://mantis.dwds.de/mantis/view.php"))

(defn prepare-issue
  [visited? {:keys [id updated status] :as issue}]
  (let [issue-id (:path (uri/uri id))
        updated  (parse-update-ts updated)]
    (assoc issue
           :url (URL. (str (uri/assoc-query mantis-issue-view "id" issue-id)))
           :updated updated
           :active? (not (#{"closed" "resolved"} status))
           :visited? (visited? [id updated]))))

(uib/bind
 (uib/funnel client/active-article client/articles visited)
 (uib/transform
  (fn [_]
    (when-let [id @client/active-article]
      (->> (some-> (@client/articles id) ::client/issues)
           (map (partial prepare-issue @visited))
           (sort-by (juxt :active? :updated) #(compare %2 %1))
           (vec)))))
 (uib/property issue-list :model))
