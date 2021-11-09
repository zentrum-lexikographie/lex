(ns zdl.lex.client.graph
  (:require [lambdaisland.uri :as uri]
            [mount.core :refer [defstate]]
            [zdl.lex.bus :as bus]
            [zdl.lex.client.http :as client.http]
            [zdl.lex.url :as lexurl]))

(def graph
  (atom nil))

(defn get-graph
  [id]
  (get (client.http/request {:url (uri/join "graph/" id)}) :body))

(defn update-graph!
  [_ {:keys [url]}]
  (let [id (lexurl/url->id url)]
    (reset! graph (get-graph id))))

(defstate graph-update
  :start (bus/listen #{:editor-activated :editor-saved} update-graph!)
  :stop (graph-update))

(comment
  (bus/publish!
   #{:editor-activated}
   {:url (lexurl/id->url "Neuartikel/Test-E_7647544.xml")}))
