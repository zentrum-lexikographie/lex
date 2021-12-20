(ns zdl.lex.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [zdl.lex.client.io :refer [lexurl-handler]]
            [zdl.lex.client.issue :as client.issue]
            [zdl.lex.client.links :as client.links]
            [zdl.lex.client.oxygen :as client.oxygen]
            [zdl.lex.client.results :as client.results]
            [zdl.lex.client.toolbar :as client.toolbar]
            [zdl.lex.url :as lexurl]
            [zdl.lex.util :refer [install-uncaught-exception-handler!]]))

(install-uncaught-exception-handler!)

(try
  (lexurl/install-stream-handler! lexurl-handler)
  (catch Throwable _))

(comment
  ;; start/stop client-side state management
  (mount/start (mount/only client.oxygen/states))
  (mount/stop (mount/only client.oxygen/states))
  ;; display dev testbed
  (ui/invoke-later
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
        :content (ui/border-panel :north client.toolbar/widget
                                  :center main-panel)))))))
