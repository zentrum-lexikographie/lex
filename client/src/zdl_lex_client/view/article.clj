(ns zdl-lex-client.view.article
  (:require [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [seesaw.forms :as forms]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.font :as font]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as ws])
  (:import com.jidesoft.hints.ListDataIntelliHints))

(def pos-hints
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
   "partizipiales Adverb"])

(defn open-create-dialog [& args]
  (let [form-input (ui/text :font (font/derived :style :bold))
        pos-input (ui/text)
        pos-hints (ListDataIntelliHints. pos-input pos-hints)
        create-article (fn [evt]
                         (try 
                           (->>
                            (http/create-article
                             (ui/value form-input)
                             (ui/value pos-input))
                            (:id)
                            (ws/open-article ws/instance))
                           (catch Exception e (timbre/warn e)))
                         (ui/dispose! evt))
        create-button (ui/button :text "Erstellen"
                                 :listen [:action create-article]
                                 :enabled? false)

        cancel-button (ui/button :text "Abbrechen"
                                 :listen [:action ui/dispose!])
        create-enabled? (uib/bind (uib/funnel form-input pos-input)
                                  (uib/transform (partial every? seq))
                                  (uib/property create-button :enabled?))
        content (forms/forms-panel
                 "right:pref, 4dlu, [100dlu, pref]"
                 :default-dialog-border? true
                 :items ["Formangabe" form-input
                         "Wortklasse" pos-input])]
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

