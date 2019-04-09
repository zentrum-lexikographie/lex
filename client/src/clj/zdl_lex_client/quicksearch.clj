(ns zdl-lex-client.quicksearch
  (:require [seesaw.core :as ui]
            [seesaw.options :refer [apply-options]]
            [seesaw.swingx :as uix]
            [seesaw.mig :as uim]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.http :as http])
  (:import [com.jidesoft.hints AbstractListIntelliHints]))


(defn form []
  (let [input (ui/text :columns 40 :font "18" :border 5)]
    (proxy [AbstractListIntelliHints] [input]
      (createList []
        (let [list (proxy-super createList)]
          (apply-options list [:background :white :font "18" :border 5])
          list))
      (updateHints [ctx]
        (let [suggestions (->> ctx str http/form-suggestions :result)
              suggestions (map (comp first :forms) suggestions)]
          (proxy-super setListData (into-array Object suggestions)))
        true)
      (acceptHint [hint]
        (proxy-super acceptHint hint)
        (timbre/info hint)))
    input))

(defn -main []
  (ui/invoke-later
   (-> (ui/frame :title "Metadatensuche" :content (form))
       ui/pack!
       ui/show!)))
