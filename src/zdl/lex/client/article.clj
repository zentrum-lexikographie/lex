(ns zdl.lex.client.article
  (:require [clojure.tools.logging :as log]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [seesaw.forms :as forms]
            [zdl.lex.bus :as bus]
            [zdl.lex.client.font :as client.font]
            [zdl.lex.client.http :as client.http]
            [zdl.lex.client.icon :as client.icon])
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

(defn create-article
  [form pos]
  (try
    (let [request  {:method       :put
                    :url          "article/"
                    :query-params {:form form
                                   :pos  pos}}
          response (client.http/request request)]
      (bus/publish! #{:open-article} {:id (get-in response [:headers "X-Lex-ID"])}))
    (catch Throwable t
      (log/warnf t "Error creating article '%s' (%s)" form pos))))

(defn open-create-dialog
  [& args]
  (let [form-input     (ui/text :font (client.font/derived :style :bold))
        pos-input      (ui/text)
        _              (ListDataIntelliHints. pos-input pos-hints)
        create-article (fn [evt]
                         (create-article (ui/value form-input)
                                         (ui/value pos-input))
                         (ui/dispose! evt))
        create-button  (ui/button :text "Erstellen"
                                  :listen [:action create-article]
                                  :enabled? false)
        cancel-button  (ui/button :text "Abbrechen"
                                  :listen [:action ui/dispose!])
        _              (uib/bind (uib/funnel form-input pos-input)
                                 (uib/transform (partial every? seq))
                                 (uib/property create-button :enabled?))
        content        (forms/forms-panel
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
        (ui/show!)
        (ui/invoke-later))))

(def create-action
  (ui/action
   :name "Artikel erstellen"
   :icon client.icon/gmd-add
   :handler open-create-dialog))

