(ns zdl-lex-client.workspace
  (:require [clojure.core.async :as async]
            [mount.core :refer [defstate]]
            [cemerick.url :refer [url]]
            [zdl-lex-client.env :refer [config]]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.article :as article])
  (:import java.net.URL
           ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace))

(def toolbar "zdl-lex-client-toolbar")

(def article-view "zdl-lex-article-view")

(def results-view "zdl-lex-results-view")

(defstate instance
  :start (proxy [StandalonePluginWorkspace] []
           (open [url]
             (timbre/info {:open (str url)})
             true)
           (showView [id request-focus?]
             (timbre/info {:id id :request-focus? request-focus?}))
           (addEditorChangeListener [_ _])
           (removeEditorChangeListener [_ _])))

(defn open-article [{:keys [id] :as article}]
  (.open ^StandalonePluginWorkspace instance
         (-> id article/id->url (URL.))))

(defn show-view
  ([id]
   (show-view id true))
  ([id request-focus?]
   (.showView ^StandalonePluginWorkspace instance id request-focus?)))
