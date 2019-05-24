(ns zdl-lex-client.article
  (:require [seesaw.core :as ui]
            [seesaw.color :as uicolor]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [clojure.string :as str]))

(def create
  (ui/action :name "Artikel erstellen"
             :icon icon/gmd-add
             :handler  (fn [_] (ui/alert "Neuer Artikel!"))))

(def delete
  (ui/action :name "Artikel löschen"
             :icon icon/gmd-delete
             :handler (fn [_] (ui/alert "Artikel löschen!"))))

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
   (-> (ui/toolbar :items (map #(ui/button :action %) [create delete]))
       #(ui/frame :title "Test" :content %)
       ui/pack!
       ui/show!)))
;;
