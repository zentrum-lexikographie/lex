(ns zdl-lex-client.article
  (:require [seesaw.core :as ui]
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

;;(ui/invoke-later (-> (ui/frame :title "Test" :content (ui/toolbar :items (map #(ui/button :action %) [create delete]))) ui/pack! ui/show!))
