(ns zdl.lex.client.preview
  (:require [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [mount.core :as mount :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl.lex.client.bus :as bus]
            [zdl.lex.client.icon :as icon]
            [zdl.lex.client.workspace :as ws]
            [zdl.lex.fs :as fs]
            [zdl.lex.url :as lexurl])
  (:import java.net.URL))

(def id (atom nil))

(defn set-id
  [^URL url]
  (reset! id (when url (lexurl/url->id url))))

(def base-url
  (uri/uri "http://zwei.dwds.de/wb/existdb/"))

(defstate remove-chrome-profile
  :start (-> (fs/file (ws/preferences-dir ws/instance) "chrome-profile")
             (fs/delete! true)))

(defstate editor->id
  :start (bus/listen #{:editor-activated :editor-deactivated}
                     (fn [topic url]
                       (set-id (when (= :editor-activated topic) url))))
  :stop editor->id)

(defn render
  [id]
  (ws/open-url ws/instance (uri/assoc-query base-url :d id)))

(defn handle-action [e]
  (let [id @id
        modified? (ws/modified? ws/instance (lexurl/id->url id))]
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
           (ui/show!)
           (ui/invoke-later)))))

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
