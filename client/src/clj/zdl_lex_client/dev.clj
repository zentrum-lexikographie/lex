(ns zdl-lex-client.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [clojure.java.io :as io]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.view.issue :as issue-view]
            [zdl-lex-client.view.results :as results-view]
            [zdl-lex-client.view.toolbar :as toolbar]
            [zdl-lex-client.workspace :as ws]
            [zdl-lex-common.log :as log]
            [zdl-lex-common.url :as lexurl])
  (:import java.awt.Toolkit
           [java.net URLStreamHandler URLConnection]))

(log/configure-slf4j-bridge)
(log/configure-timbre)

(defn show-testbed []
  (let [screen-size (.. (Toolkit/getDefaultToolkit) (getScreenSize))
        width  (max 800 (- (.getWidth screen-size) 200))
        height (max 600 (- (.getHeight screen-size) 200))

        main-panel (ui/splitter :left-right
                                results-view/tabbed-pane
                                (issue-view/create-panel)
                                :divider-location 0.75)]
    (try
      (lexurl/install-stream-handler! http/api-store-lexurl-handler)
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

  (time (http/get-issues "Leder"))
  (mount/start)
  (mount/stop)

  (http/sync-with-exist "DWDS/MWA-001/der_Grosse_Teich.xml")

  (search/request "forms:plexi*")

  ws/instance)
