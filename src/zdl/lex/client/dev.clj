(ns zdl.lex.client.dev
  (:require [seesaw.core :as ui]
            [zdl.lex.client.io :refer [lexurl-handler]]
            [zdl.lex.client.issue :as client.issue]
            [zdl.lex.client.links :as client.links]
            [zdl.lex.client.results :as client.results]
            [zdl.lex.client.toolbar :as client.toolbar]
            [zdl.lex.client.util :as client.util]
            [zdl.lex.url :as lexurl]
            [zdl.lex.util :refer [install-uncaught-exception-handler!]]
            [integrant.core :as ig]))

(install-uncaught-exception-handler!)

(try
  (lexurl/install-stream-handler! lexurl-handler)
  (catch Throwable _))

(defmethod ig/init-key ::testbed
  [_ _]
  (ui/invoke-now
   (ui/show!
    (ui/pack!
     (let [sidebar    (ui/splitter :top-bottom
                                   client.links/pane
                                   client.issue/panel
                                   :divider-location 0.6
                                   :resize-weight 0.6)
           main-panel (ui/splitter :left-right
                                   client.results/pane
                                   sidebar
                                   :divider-location 0.75
                                   :resize-weight 0.75)]
       (ui/frame
        :title "zdl-lex-client/dev"
        :size (client.util/clip-to-screen-size)
        :content (ui/border-panel :north client.toolbar/widget
                                  :center main-panel)))))))

(defmethod ig/halt-key! ::testbed
  [_ testbed]
  (ui/dispose! testbed))
