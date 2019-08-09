(ns zdl-lex-client.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.bus :as bus]
            [zdl-lex-client.editors :as editors]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.view.article :as article-view]
            [zdl-lex-client.view.results :as results-view]
            [zdl-lex-client.view.toolbar :as toolbar]
            [zdl-lex-client.workspace :as ws])
  (:import java.awt.Toolkit
           ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace))

(defn show-testbed []
  (let [screen-size (.. (Toolkit/getDefaultToolkit) (getScreenSize))
        width  (max 800 (- (.getWidth screen-size) 200))
        height (max 600 (- (.getHeight screen-size) 200))

        main-panel (ui/splitter :left-right
                                results-view/tabbed-pane
                                article-view/panel
                                :divider-location 0.8)]
    (mount/stop)
    (mount/start)
    (ui/invoke-later
     (ui/show!
      (ui/frame
       :title "zdl-lex-client/dev"
       :size [width :by height]
       :content (ui/border-panel
                 :north toolbar/widget
                 :center main-panel))))))

(comment
  (show-testbed)

  (mount/start)
  (mount/stop)

  (http/sync-with-exist "DWDS/MWA-001/der_Grosse_Teich.xml")

  (search/request "forms:plexi*")

  ws/instance)
