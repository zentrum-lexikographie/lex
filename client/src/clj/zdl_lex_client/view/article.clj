(ns zdl-lex-client.view.article
  (:require [clojure.core.memoize :as memoize]
            [mount.core :as mount :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.border :refer [empty-border line-border]]
            [seesaw.core :as ui]
            [seesaw.forms :as forms]
            [seesaw.mig :as mig]
            [taoensso.timbre :as timbre]
            [tick.alpha.api :as t]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.font :as font]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as ws])
  (:import com.jidesoft.hints.ListDataIntelliHints
           java.awt.event.MouseEvent))

(def form-input
  (ui/text :font (font/derived :style :bold)))

(def pos-input (ui/text))

(def ^:private pos-hints
  (ListDataIntelliHints.
   pos-input
   ["Adjektiv"
    "Adverb"
    "Affix"
    "Ausruf"
    "Eigenname"
    "Kardinalzahl"
    "Konjunktion"
    "Mehrwortausdruck"
    "Pronominaladverb"
    "Präposition"
    "Substantiv"
    "Verb"
    "partizipiales Adjektiv"
    "partizipiales Adverb"]))

(defn create-article [evt]
  (try 
    (->>
     (http/create-article (ui/value form-input) (ui/value pos-input))
     (:id)
     (ws/open-article ws/instance))
    (catch Exception e (timbre/warn e)))
  (ui/dispose! evt))

(def create-button
  (ui/button :text "Erstellen" :listen [:action create-article]  :enabled? false))

(def cancel-button
  (ui/button :text "Abbrechen"  :listen [:action ui/dispose!]))

(defstate create-enabled?
  :start (uib/bind (uib/funnel form-input pos-input)
                   (uib/transform (partial every? seq))
                   (uib/property create-button :enabled?))
  :stop (create-enabled?))

(comment
  (mount/start #'create-enabled?)
  (mount/stop))

(defn open-create-dialog [& args]
  (let [content (forms/forms-panel
                 "right:pref, 4dlu, [100dlu, pref]"
                 :default-dialog-border? true
                 :items ["Formangabe" form-input
                         "Wortklasse" pos-input])]
    (ui/config! form-input :text "")
    (ui/config! pos-input :text "")
    (-> (ui/dialog :title "Neuen Artikel anlegen"
                   :type :plain
                   :content content
                   :parent (some-> args first ui/to-root)
                   :options [create-button cancel-button])
        (ui/pack!)
        (ui/show!))))

(def create-action
  (ui/action
   :name "Artikel erstellen"
   :icon icon/gmd-add
   :handler open-create-dialog))

(defn- severity->border [severity]
  (let [color (condp = severity
                "feature" :grey
                "trivial" :grey
                "text"  :grey
                "tweak" :grey
                "minor" :yellow
                "major" :orange
                "crash" :red
                "block" :red
                :orange)]
    [5
     (line-border :color color :left 10)
     (line-border :thickness 5 :color :white)]))

(def visited-issues (atom #{}))

(defn open-issue [{:keys [id last-updated url]} ^MouseEvent e]
  (when (= 2 (.getClickCount e))
    (swap! visited-issues conj [id last-updated])
    (ws/open-url ws/instance url)))

(def issues-panel (ui/grid-panel :columns 1 :items []))

(def date-time-formatter (t/formatter "dd.mm.YYYY, HH:MM"))

(defn render-issue
  [{:keys [active? last-updated resolution severity status summary visited?]
    :as issue}]
  (let [fg-color (if active? :black :lightgray)
        bg-color (if active? :snow :white)
        visited-color (if visited? :green fg-color)
        label (partial ui/label :foreground fg-color)
        text (partial label :font (font/derived :style :plain))
        last-updated (t/format date-time-formatter last-updated)]
    (mig/mig-panel
     :cursor :hand
     :background bg-color
     :border (severity->border severity)
     :listen [:mouse-pressed (partial open-issue issue)]
     :items [[(label :icon icon/gmd-bug-report
                     :foreground visited-color
                     :text summary
                     :tip summary
                     :border [(empty-border :bottom 2)
                              (line-border :color fg-color :bottom 1)])
              "span 2, wrap"]
             [(label :text "Datum")] [(text :text last-updated) "wrap"]
             [(label :text "Severity")] [(text :text severity) "wrap"]
             [(label :text "Status")] [(text :text status) "wrap"]
             [(label :text "Resolution")] [(text :text resolution)]])))

(def cached-get-issues
  (memoize/ttl http/get-issues :ttl/threshold (* 15 60 1000)))

(defn render-issues [[{:keys [forms]} visited?]]
  (let [visited? (or visited? @visited-issues)]
    (->> (mapcat cached-get-issues forms)
         (map (fn [{:keys [id last-updated status] :as issue}]
                (let [last-updated (t/parse last-updated)]
                  (assoc issue
                         :last-updated last-updated
                         :active? (not (#{"closed" "resolved"} status))
                         :visited? (visited? [id last-updated])))))
         (sort-by #(vector (:active? %) (:last-updated %)) #(compare %2 %1))
         (map render-issue))))

(defstate article->issues
  :start (uib/bind (uib/funnel (bus/bind :article) visited-issues)
                   (uib/transform render-issues)
                   (uib/property issues-panel :items))
  :stop (article->issues))

(def panel (ui/scrollable issues-panel :hscroll :never))

(comment
  (let [sample {:category "MWA-Link",
                :last-updated (t/parse "2019-08-21T12:02:10+02:00"),
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
    (->> (render-issue sample) (ui/frame :size [200 :by 600] :content) ui/show!)))
