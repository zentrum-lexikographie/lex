(ns zdl-lex-client.view.article
  (:require [mount.core :refer [defstate]]
            [seesaw.bind :as uib]
            [seesaw.core :as ui]
            [zdl-lex-client.article :as carticle]
            [zdl-lex-common.article :as article]
            [zdl-lex-client.bus :as bus]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [zdl-lex-client.workspace :as ws]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.xml :as xml]))

(def active (ui/text :multi-line? true
                     :editable? false
                     :wrap-lines? true
                     :margin 5
                     :text "-"))

(defstate editor-xml
  :start (let [subscription (bus/subscribe :editor-active)]
           (-> (fn [[url active?]]
                 (when active?
                   (->
                    (d/chain (d/future (ws/xml-document ws/instance url))
                             #(some->> (article/doc->articles %)
                                       (map article/excerpt)
                                       (first)
                                       (merge {:url url}))
                             (partial bus/publish! :article-excerpt))
                    (d/catch #(timbre/warn %)))))
               (s/consume subscription))
           subscription)
  :stop (s/close! editor-xml))

(defstate active-text
  :start (uib/bind (bus/bind :article-excerpt)
                   (uib/transform str)
                   (uib/property active :text))
  :stop (active-text))

(def panel (ui/scrollable (ui/border-panel :center active)))
