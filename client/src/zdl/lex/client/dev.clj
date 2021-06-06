(ns zdl.lex.client.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [zdl.lex.client.oxygen.url-handler :refer [lexurl-handler]]
            [zdl.lex.client.view.graph :as graph-view]
            [zdl.lex.client.view.issue :as issue-view]
            [zdl.lex.client.view.results :as results-view]
            [zdl.lex.client.view.toolbar :as toolbar]
            [zdl.lex.url :as lexurl]
            [zdl.lex.client.status :as status]))

(def main-content
  (ui/splitter :left-right
               results-view/tabbed-pane
               (ui/top-bottom-split graph-view/panel issue-view/panel
                                    :divider-location 0.5 :resize-weight 0.5)
               :divider-location 0.75 :resize-weight 0.75))
(def frame
  (ui/frame
   :title "zdl-lex-client/dev"
   :content (ui/border-panel :north toolbar/widget :center main-content)))

(defn show-testbed
  []
  (try (lexurl/install-stream-handler! lexurl-handler) (catch Throwable t))
  (mount/stop)
  (mount/start)
  (status/trigger!)
  (ui/invoke-later (ui/show! (ui/pack! frame))))

(comment
  (show-testbed))
