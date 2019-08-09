(ns zdl-lex-client.view.article
  (:require [manifold.deferred :as d]
            [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.workspace :as ws]
            [zdl-lex-common.article :as article]))

(def active (ui/text :multi-line? true
                     :editable? false
                     :wrap-lines? true
                     :margin 5
                     :text "-"))

(defstate editor->article
  :start (let [subscription (bus/subscribe :editor-active)]
           (-> (fn [[url active?]]
                 (when active?
                   (->
                    (d/chain (d/future (ws/xml-document ws/instance url))
                             #(some->> (article/doc->articles %)
                                       (map article/excerpt)
                                       (first)
                                       (merge {:url url}))
                             (partial bus/publish! :article))
                    (d/catch #(timbre/warn %)))))
               (s/consume subscription))
           subscription)
  :stop (s/close! editor->article))

(defstate active-text
  :start (uib/bind (bus/bind :article)
                   (uib/transform str)
                   (uib/property active :text))
  :stop (active-text))

(def panel (ui/scrollable (ui/border-panel :center active)))
