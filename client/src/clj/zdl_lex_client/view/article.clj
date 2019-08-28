(ns zdl-lex-client.view.article
  (:require [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.forms :as forms]
            [seesaw.core :as ui]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.font :as font]
            [mount.core :as mount]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.workspace :as ws])
  (:import com.jidesoft.hints.ListDataIntelliHints))

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

(def active (ui/text :multi-line? true
                     :editable? false
                     :wrap-lines? true
                     :margin 5
                     :text "-"))

(defstate active-text
  :start (uib/bind (bus/bind :article)
                   (uib/transform str)
                   (uib/property active :text))
  :stop (active-text))

(def panel (ui/scrollable (ui/border-panel :center active)))
