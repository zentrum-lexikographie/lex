(ns zdl-lex-client.preview
  (:require [cemerick.url :refer [url url-encode]]
            [clojure.string :as str]
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
  :start (bus/listen :editor-active (fn [[url active?]] (set-id (if active? url))))
  :stop (editor->id))

(defn render [id]
  (some->>
   (merge url-base {:query {:d (-> (str "dwdswb/data/" id) (url-encode))}})
   (str) (URL.)
   (ws/open-url ws/instance)))

(defn handle-action [e]
  (let [id @id
        modified? (ws/modified? ws/instance (http/id->url id))]
    (if-not modified?
      (render id)
      (->> ["Der aktuelle Artikel ist nicht gespeichert."
            "Bitte speichern Sie ihre Arbeit, um eine aktuelle Vorschau zu erhalten."]
           (str/join \newline)
           (ui/dialog :parent (ui/to-root e)
                      :modal? true
                      :type :error
                      :content)
           (ui/pack!)
           (ui/show!)))))

(def action
  (ui/action :name "Artikelvorschau" :icon icon/gmd-web
             :enabled? false :handler handle-action))

(defstate action-enabled?
  :start (uib/bind id
                   (uib/transform some?)
                   (uib/property action :enabled?))
  :stop (action-enabled?))

(comment
  (mount/start #'ws/instance #'remove-chrome-profile)
  (mount/stop)
  (render "DWDS/MWA-001/heisser Draht.xml"))
