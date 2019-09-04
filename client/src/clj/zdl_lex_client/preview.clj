(ns zdl-lex-client.preview
  (:require [cemerick.url :refer [url]]
            [me.raynes.fs :as fs]
            [mount.core :as mount :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.workspace :as ws])
  (:import java.net.URL))

(def id (atom nil))

(defn set-id [^URL url]
  (reset! id (if url (http/url->id url))))

(def url-base (url "http://zwei.dwds.de/wb/existdb/"))

(defstate remove-chrome-profile
  :start (fs/delete-dir
          (fs/file (ws/preferences-dir ws/instance) "chrome-profile")))

(defstate editor->id
  :start [
          (bus/listen :editor-active
                      (fn [[url active?]]
                        (let [saved? (not (ws/modified? ws/instance url))]
                          (set-id (if (and active? saved?) url)))))
          (bus/listen :editor-saved
                      (fn [[url]]
                        (set-id url)))]
  :stop (doseq [listener editor->id] (listener)))

(defn render [id]
  (some->>
   (merge url-base {:query {:d (str "dwdswb/data/" id)}})
   (str) (URL.)
   (ws/open-url ws/instance)))

(def action
  (ui/action :name "Artikelvorschau"
             :enabled? false
             :icon icon/gmd-web
             :handler (fn [_] (render @id))))

(defstate action-enabled?
  :start (uib/bind id
                   (uib/transform some?)
                   (uib/property action :enabled?))
  :stop (action-enabled?))

(comment
  (mount/start #'ws/instance #'remove-chrome-profile)
  (mount/stop)
  (render "Neuartikel-004/Lokalgesprach-E_7637106.xml"))
