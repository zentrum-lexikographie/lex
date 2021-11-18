(ns zdl.lex.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [zdl.lex.client.io :refer [lexurl-handler]]
            [zdl.lex.client.graph :as client.graph]
            [zdl.lex.client.issue :as client.issue]
            [zdl.lex.client.results :as client.results]
            [zdl.lex.client.toolbar :as client.toolbar]
            [zdl.lex.url :as lexurl]))

(require 'zdl.lex.client.oxygen)
(require 'zdl.lex.util)

(try
  (lexurl/install-stream-handler! lexurl-handler)
  (catch Throwable _))

(comment
  ;; start client-side state management
  (mount/start)
  ;; display dev testbed
  (ui/invoke-later
   (ui/show!
    (ui/pack!
     (let [sidebar    client.graph/pane
           main-panel (ui/splitter :left-right
                                   client.results/pane
                                   sidebar
                                   :divider-location 0.75
                                   :resize-weight 0.75)]
       (ui/frame
        :title "zdl-lex-client/dev"
        :content (ui/border-panel :north client.toolbar/widget
                                  :center main-panel)))))))
