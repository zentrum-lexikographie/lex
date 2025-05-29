(ns zdl.lex.dev
  (:require
   [seesaw.core :as ui]
   [zdl.lex.ui.issue :as issue]
   [zdl.lex.ui.links :as links]
   [zdl.lex.ui.search :as search]
   [zdl.lex.ui.toolbar :as toolbar]
   [zdl.lex.ui.util :as util]
   [seesaw.bind :as uib]
   [zdl.lex.client :as client]
   [zdl.lex.article :as article]))

(def sidebar
  (ui/splitter :top-bottom
               links/pane
               issue/panel
               :divider-location 0.6
               :resize-weight 0.6))

(def editor
  (ui/text :border 5
           :font {:from :monospaced :style :bold :size (util/large-font-size)}
           :foreground "#999"
           :multi-line? true
           :editable? false))

(uib/bind
 (uib/funnel client/active-article client/articles)
 (uib/transform
  (fn [_]
    (let [active-article @client/active-article
          articles       @client/articles]
      (some-> active-article articles ::client/xml article/->str))))
 (uib/value editor))

(def article-panel
  (ui/splitter :top-bottom
               search/panel
               (ui/scrollable editor)
               :divider-location 0.75
               :resize-weight 0.75))

(def frame
  (ui/frame
   :title "ZDL Lex – Dev"
   :size (util/clip-to-screen-size)
   :content (ui/border-panel
             :north toolbar/widget
             :center (ui/splitter :left-right
                                  article-panel
                                  sidebar
                                  :divider-location 0.75
                                  :resize-weight 0.75))))

(defn show!
  []
  (-> frame ui/show! ui/invoke-now))

(defn dispose!
  []
  (ui/dispose! frame))

(uib/subscribe
 search/opened-article-id
 (fn [id]
   (client/dissoc-article @client/active-article)
   (client/update-article id #(client/http-get-article id))
   (reset! client/active-article id)))
