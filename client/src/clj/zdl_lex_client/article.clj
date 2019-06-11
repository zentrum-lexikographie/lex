(ns zdl-lex-client.article
  (:require [cemerick.url :refer [url]]
            [seesaw.core :as ui]
            [seesaw.color :as uicolor]
            [zdl-lex-client.env :refer [config]]
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
     "Lex-zur_Abgabe" "#98fb98" ; "#ffff00"
     "Red-1" "#ffec8b"
     "Red-f" "#aeecff" ; "#ccffcc"
     "#ffffff")))

(def ^:private base (config :webdav-base))

(defn id->url [id] (->> id (url base) str))

(defn url->id [u]
  (if (str/starts-with? u base)
    (subs u (inc (count base)))))

(comment
  (id->url "test.xml")
  (url->id "http://spock.dwds.de:8080/exist/webdav/db/dwdswb/data/test.xml")
  (ui/invoke-later
   (-> (ui/frame :title "Test" :content (ui/button :action create))
       ui/pack!
       ui/show!)))
