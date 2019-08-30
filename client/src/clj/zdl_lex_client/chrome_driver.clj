(ns zdl-lex-client.chrome-driver
  (:require [diehard.core :as dh]
            [environ.core :refer [env]]
            [etaoin.api :as web]
            [me.raynes.fs :as fs]
            [mount.core :as mount :refer [defstate]]
            [seesaw.core :as ui]
            [seesaw.bind :as uib]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.icon :as icon]
            [zdl-lex-client.oxygen.plugin :as plugin]
            [zdl-lex-client.workspace :as ws]
            [zdl-lex-common.xml :as xml]))

(defn profile-dir []
  (doto (fs/file (ws/preferences-dir ws/instance) "chrome-profile")
    (fs/mkdirs)))

(defn driver-file []
  (let [dir (->> (filter some? [(plugin/base-dir) (fs/file "..")])
                 (map #(fs/file % "chrome-driver"))
                 (map (comp fs/normalized fs/absolute))
                 (first))
        file (if (.. (env :os-name) (toLowerCase) (contains "windows"))
               "chromedriver.exe"
               "chromedriver")]
    (fs/chmod "+x" (fs/file dir file))))

(def driver (atom nil))

(defn start-driver []
  (locking driver
    (->> (web/chrome {:path-driver (str (driver-file))
                      :profile (str (profile-dir))})
         (reset! driver))))

(defn stop-driver []
  (locking driver
    (some-> @driver web/quit)
    (reset! driver nil)))

(defstate shutdown-driver
  :start (.. (Runtime/getRuntime) (addShutdownHook (Thread. stop-driver))))

(defn with-driver [cb]
  (locking driver
    (when-not @driver (start-driver))
    (dh/with-retry {:retry-on Exception
                    :max-retries 1
                    :on-failed-attempt (fn [_ _ ] (stop-driver))
                    :on-retry (fn [_ _] (start-driver))}
      (cb @driver))))

(let [wb-form {:tag :form :action "http://zwei.dwds.de/wb"}]
  (defn render-preview [xml]
    (future 
      (with-driver
        (fn [chrome]
          (doto chrome
            (web/go "http://zwei.dwds.de/admin/wb")
            (web/wait-visible {:id :xml} {:timeout 60 :interval 3})
            (web/js-execute "document.querySelector(\"#xml\").value = arguments[0];" xml)
            (web/click [wb-form {:tag :button :type :submit}])))))))


(def editor-url (atom nil))

(defstate editor-active->url
  :start (bus/listen :editor-active
                     (fn [[url active?]]
                       (reset! editor-url (if active? url))))
  :stop (editor-active->url))

(def preview-action
  (ui/action :name "Artikelvorschau"
             :enabled? false
             :icon icon/gmd-web
             :handler (fn [_] (some->> @editor-url
                                       (ws/xml-document ws/instance)
                                       (xml/serialize)
                                       (render-preview)))))

(defstate preview-action-enabled?
  :start (uib/bind editor-url
                   (uib/transform some?)
                   (uib/property preview-action :enabled?))
  :stop (preview-action-enabled?))

(comment
  (mount/start #'ws/instance)
  (mount/stop)
  (-> "../data/git/articles/Neuartikel-004/Lokalgesprach-E_7637106.xml"
      (slurp :encoding "UTF-8")
      (render-preview)))
