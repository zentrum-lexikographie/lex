(ns zdl-lex-client.article
  (:require [seesaw.core :as ui]
            [seesaw.color :as uicolor]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [clojure.string :as str]))

(def create
  (ui/action
   :name "Artikel erstellen"
   :icon icon/gmd-add
   :handler
   (fn [_]
     (->
      (ui/dialog :content "Neuer Artikel!"
                 :option-type :ok-cancel
                 :modal? true
                 :success-fn #(ui/dispose! %)
                 :cancel-fn #(ui/dispose! %))
      (ui/pack!)
      (ui/show!)))))

(defn status->color [{:keys [status]}]
  (uicolor/color
   (condp = status
     "Artikelrumpf" "#ffcccc"
     "Lex-zur_Abgabe" "#ffff00"
     "Red-1" "#ffec8b"
     "Red-f" "#ccffcc"
     "#ffffff")))

(comment
  (ui/invoke-later
   (-> (ui/frame :title "Test" :content (ui/button :action create))
       ui/pack!
       ui/show!)))
;;
