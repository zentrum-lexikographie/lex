(ns zdl-lex-client.dev
  (:require [mount.core :as mount :refer [defstate]]
            [seesaw.core :as ui]
            [seesaw.swingx :as uix]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.oxygen.url-handler :refer [lexurl-handler]]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.view.issue :as issue-view]
            [zdl-lex-client.view.results :as results-view]
            [zdl-lex-client.view.toolbar :as toolbar]
            [zdl-lex-client.workspace :as ws]
            [zdl-lex-common.url :as lexurl]
            [zdl-lex-client.bus :as bus]
            [clojure.tools.logging :as log])
  (:import java.awt.Toolkit))

(defn show-testbed []
  (try
    (lexurl/install-stream-handler! lexurl-handler)
    (catch Throwable t))
  (mount/stop)
  (mount/start)
  (ui/invoke-later
   (-> (ui/frame
        :title "zdl-lex-client/dev"
        ;;:size [800 :by 600]
        :content (ui/border-panel
                  :north toolbar/widget
                  :center (ui/splitter
                           :left-right
                           results-view/tabbed-pane
                           issue-view/panel
                           :divider-location 0.75
                           :resize-weight 0.75)))
       (ui/pack!)
       (ui/show!))))

(comment
  (show-testbed))
