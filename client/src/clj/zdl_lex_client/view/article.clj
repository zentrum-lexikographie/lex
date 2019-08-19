(ns zdl-lex-client.view.article
  (:require [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.http :as http]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [zdl-lex-common.xml :as xml]
            [tick.alpha.api :as t]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as ws])
  (:import java.text.Normalizer
           java.text.Normalizer$Form))

;;<templates>
;;<template name="Standard">${PluginDir}/templates/template-generic.xml</template>
;;<template name="Nomen">${PluginDir}/templates/template-N.xml</template>
;;<template name="Verb">${PluginDir}/templates/template-V.xml</template>
;;<template name="Adjektiv">${PluginDir}/templates/template-ADJ.xml</template>
;;</templates>

(def xml-template (slurp (io/resource "template.xml") :encoding "UTF-8"))

(defn generate-id []
  (let [candidate #(str "E_" (rand-int 10000000))]
    (loop [id (candidate)]
      (if-not (http/id-exists? id) id
              (recur (candidate))))))

(defn form->filename [form]
  (-> form
      (Normalizer/normalize Normalizer$Form/NFD)
      (str/replace #"\p{InCombiningDiacriticalMarks}" "")
      (str/replace "ß" "ss")
      (str/replace " " "-")
      (str/replace #"[^\p{Alpha}\p{Digit}\-]" "_")))

(def ^:private new-article-collection "Neuartikel-004")

(defn new-article [form pos author]
  (let [xml-id (generate-id)
        filename (form->filename form)
        id (str new-article-collection "/" filename "-" xml-id ".xml")
        doc (xml/parse xml-template)
        element-by-name #(-> (.getElementsByTagName doc %) xml/nodes->seq first)]
    (doto (element-by-name "Artikel")
      (.setAttribute "xml:id" xml-id)
      (.setAttribute "Zeitstempel" (t/format :iso-local-date (t/date)))
      (.setAttribute "Autor" author))
    (.. (element-by-name "Schreibung") (setTextContent form))
    (.. (element-by-name "Wortklasse") (setTextContent pos))
    (ws/create-article ws/instance id doc)))

(def create-action
  (ui/action
   :name "Artikel erstellen"
   :icon icon/gmd-add
   :handler
   (fn [_]
     (->
      (ui/dialog :content "Neuer Artikel!"
                 :option-type :ok-cancel
                 :modal? true
                 :success-fn (fn [e]
                               (new-article "trockene Tücher"
                                            "Mehrwortausdruck"
                                            "middell")
                               (ui/dispose! e))
                 :cancel-fn #(ui/dispose! %))
      (ui/pack!)
      (ui/show!)))))

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
