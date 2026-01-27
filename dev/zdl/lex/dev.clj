(ns zdl.lex.dev
  (:require
   [integrant.core :as ig]
   [seesaw.bind :as uib]
   [seesaw.core :as ui]
   [zdl.lex.ui.issue :as issue]
   [zdl.lex.ui.links :as links]
   [zdl.lex.ui.search :as search]
   [zdl.lex.ui.toolbar :as toolbar]
   [zdl.lex.ui.util :as util]
   [zdl.lex.client :as client]
   [zdl.lex.article :as article]
   [zdl.lex.ui.gpt :as gpt]))

(def editor
  (ui/text :border 5
           :font {:from :monospaced :style :bold :size (util/large-font-size)}
           :foreground "#999"
           :multi-line? true
           :editable? false))


(def article-panel
  (ui/border-panel
   :north toolbar/widget
   :center (ui/splitter :left-right
                        (ui/splitter :top-bottom
                                     search/panel
                                     (ui/scrollable editor)
                                     :divider-location 0.75
                                     :resize-weight 0.75)
                        (ui/splitter :top-bottom
                                     (ui/splitter
                                      :top-bottom
                                      links/pane
                                      issue/panel
                                      :divider-location 0.5
                                      :resize-weight 0.5)
                                     gpt/panel
                                     :divider-location 0.4
                                     :resize-weight 0.4)
                        :divider-location 0.75
                        :resize-weight 0.75)))


(def frame
  (ui/frame
   :title   "ZDL Lex – Dev"
   :size    (util/clip-to-screen-size)
   :content article-panel #_(ui/scrollable examples/table)))

(defmethod ig/init-key ::console
  [_ _]
  (ui/invoke-now (ui/show! frame))
  frame)

(defmethod ig/halt-key! ::console
  [_ frame]
  (ui/dispose! frame))

(defmethod ig/init-key ::search->article
  [_ _]
  (uib/subscribe
   search/opened-article-id
   (fn [id]
     (client/dissoc-article @client/active-article)
     (client/update-article id #(client/http-get-article id))
     (reset! client/active-article id))))

(defmethod ig/halt-key! ::search->article
  [_ subscription]
  (subscription))

(defmethod ig/init-key ::article->editor
  [_ _]
  (uib/bind
   (uib/funnel client/active-article client/articles)
   (uib/transform
    (fn [_]
      (let [active-article @client/active-article
            articles       @client/articles]
        (some-> active-article articles ::client/xml article/->str))))
   (uib/value editor)))

(defmethod ig/halt-key! ::article->editor
  [_ subscription]
  (subscription))

(def config
  {::console         {}
   ::search->article {}
   ::article->editor {}})
