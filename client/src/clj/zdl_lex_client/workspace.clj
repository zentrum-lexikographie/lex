(ns zdl-lex-client.workspace
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [cemerick.url :refer [url]]
            [zdl-lex-client.env :refer [config]]
            [taoensso.timbre :as timbre])
  (:import java.net.URL
           ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace))

(def toolbar "zdl-lex-client-toolbar")

(def results-view "zdl-lex-results-view")

(defonce instance (atom nil))

(defn open-article [{:keys [id] :as article}]
  (if-let [^StandalonePluginWorkspace instance @instance]
    (.open instance (-> (url (config :webdav-base) "/articles" id) str (URL.)))
    (timbre/info article)))

(defn show-view
  ([id]
   (show-view id true))
  ([id request-focus]
   (if-let [^StandalonePluginWorkspace instance @instance]
     (.showView instance id request-focus)
     (timbre/info {:id id :request-focus request-focus}))))
