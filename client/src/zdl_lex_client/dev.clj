(ns zdl-lex-client.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.oxygen.url-handler :refer [lexurl-handler]]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.view.issue :as issue-view]
            [zdl-lex-client.view.results :as results-view]
            [zdl-lex-client.view.toolbar :as toolbar]
            [zdl-lex-client.workspace :as ws]
            [zdl-lex-common.url :as lexurl])
  (:import java.awt.Toolkit))

(defn show-testbed []
  (let [screen-size (.. (Toolkit/getDefaultToolkit) (getScreenSize))
        width  (max 800 (- (.getWidth screen-size) 200))
        height (max 600 (- (.getHeight screen-size) 200))

        main-panel (ui/splitter :left-right
                                results-view/tabbed-pane
                                (issue-view/create-panel)
                                :divider-location 0.75)]
    (try
      (lexurl/install-stream-handler! lexurl-handler)
      (catch Throwable t))

    (mount/stop)
    (mount/start)
    (ui/invoke-later
     (-> (ui/frame
          :title "zdl-lex-client/dev"
          :size [1200 :by 800]
          :content (ui/border-panel
                    :north (toolbar/widget)
                    :center main-panel))
         (ui/show!)))))

(comment
  (show-testbed)
  (mount/start)
  (mount/stop)

  (search/request "forms:plexi*")

  ws/instance)
