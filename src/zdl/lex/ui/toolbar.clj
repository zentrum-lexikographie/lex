(ns zdl.lex.ui.toolbar
  (:require
   [clojure.java.io :as io]
   [seesaw.bind :as uib]
   [seesaw.forms :as forms]
   [seesaw.core :as ui]
   [taoensso.telemere :as tm]
   [zdl.lex.ui.search :as search]
   [zdl.lex.ui.util :as util]
   [zdl.lex.ui.qa :as qa]
   [zdl.lex.oxygen.workspace :as workspace]
   [zdl.lex.client :as client])
  (:import
   (java.awt Color)
   (com.jidesoft.hints ListDataIntelliHints)
   (ro.sync.exml.workspace.api.standalone.ui ToolbarButton)))

(defn auth->status-label
  [[user _]]
  (or user "<nicht angemeldet>"))

(def status-label
  (ui/label :text (auth->status-label @client/auth) :border 5))

(uib/bind
 client/auth
 (uib/transform auth->status-label)
 (uib/property status-label :text))

(def search-all-action
  (ui/action :name "Alle Artikel"
             :icon (util/icon :star)
             :handler (fn [_] (client/query "*"))))

(def pos-hints
  ["Adjektiv"
   "Adverb"
   "Affix"
   "Eigenname"
   "Interjektion"
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
    (-> (client/http-create-article form pos) (workspace/open-article))
    (catch Throwable t (tm/error! t))))

(defn open-create-dialog
  [& args]
  (let [form-input     (ui/text :font (util/derived-font :style :bold))
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
   :icon (util/icon :add)
   :handler open-create-dialog))

(def help-pane
  (doto (ui/scrollable
         (doto (ui/styled-text :editable? false
                               :wrap-lines? true
                               :background :white
                               :margin 10)
           (.setContentType "text/html")
           (.setText (slurp (io/resource "zdl/lex/ui/help.html")))))
    (.. (getViewport) (setBackground Color/WHITE))))

(defn show-help
  [& args]
  (ui/invoke-later
    (ui/scroll! help-pane :to :top)
    (ui/show!
     (ui/dialog :title "Hilfe"
                :content help-pane
                :parent (some-> args first ui/to-root)
                :modal? true
                :size [800 :by 600]))))

(def show-help-action
  (ui/action :name "Hilfe"
             :icon (util/icon :help)
             :handler show-help))

(def preview-action
  (ui/action :name "Artikelvorschau"
             :icon (util/icon :web)
             :handler (fn [_] (workspace/preview-article))))

(def components
  [(ui/label :icon "zdl/lex/ui/logo.png" :border 6 :size [32 :by 32])
   status-label
   search/input
   (ToolbarButton. search/action false)
   (ToolbarButton. search-all-action false)
   (ToolbarButton. create-action false)
   (ToolbarButton. show-help-action false)
   (ToolbarButton. preview-action false)
   (ToolbarButton. qa/action false)])

(def widget
  (ui/toolbar :floatable? false :orientation :horizontal :items components))
