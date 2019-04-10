(ns zdl-lex-client.metasearch
  (:require [seesaw.core :as ui]
            [seesaw.mig :as uim]))


;; …, Schreibung, Element, Status, Änderung
;; Liste kopieren, Öffnen, Schließen
(defn form []
  (ui/border-panel
   :north (uim/mig-panel
           :items [["Datum:"]
                   [(ui/text) "growx"]
                   [(ui/checkbox) "wrap"]

                   [(ui/combobox :model [:a :b :c])]
                   [(ui/combobox :model [1 2 3]) "growx"]
                   [(ui/button :text "-") "wrap"]

                   [(ui/button :text "+")]]
           :constraints ["insets 5"
                         "[align right][grow][]"
                         "[align baseline]"])
   :center (ui/scrollable
            (ui/table
             :model [:columns [{:key :num :text "…" :class clojure.lang.BigInt}
                               {:key :surface :text "Schreibung"}
                               {:key :status :text "Status"}
                               {:key :changed :text "Änderung"}]
                     :rows    [{:num 1
                                :surface "laufen"
                                :status "Entwurf"
                                :changed "2019-01-01 20:00:00"}
                               {:num 2
                                :surface "rennen"
                                :status "Freigabe"
                                :changed "2019-02-01 20:00:00"}
                               {:num 3
                                :surface "spazieren"
                                :status "Neu"
                                :changed "2019-01-15 20:00:00"}]]))
   :south (ui/flow-panel
           :items [(ui/button :text "Liste kopieren" :mnemonic \l)
                   (ui/button :text "Öffnen" :mnemonic \n)
                   (ui/button :text "Schließen" :mnemonic \s)]
           :align :right :hgap 5 :vgap 5)))

(defn -main []
  (ui/invoke-later
   (-> (ui/frame :title "Metadatensuche"
                 :size [400 :by 800]
                 :content (form))
       ui/show!)))
