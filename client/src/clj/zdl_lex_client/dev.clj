(ns zdl-lex-client.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [etaoin.api :as web]
            [etaoin.keys :as web-keys]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.view.article :as article-view]
            [zdl-lex-client.view.results :as results-view]
            [zdl-lex-client.view.toolbar :as toolbar]
            [zdl-lex-client.workspace :as ws])
  (:import java.awt.Toolkit))

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
     (-> (ui/frame
          :title "zdl-lex-client/dev"
          :size [width :by height]
          :content (ui/border-panel
                    :north toolbar/widget
                    :center main-panel))
         (ui/pack!)
         (ui/show!)))))

(comment
  (show-testbed)

  (let [wb-form {:tag :form :action "http://zwei.dwds.de/wb"}
        xml (slurp "../data/git/articles/Neuartikel-004/zufallsgesteuert-E_8306451.xml":encoding "UTF-8")]
    (web/with-chrome {:profile "/home/middell/.config/google-chrome/Profile 1"} b
      (doto b
        (web/maximize)
        (web/go "http://zwei.dwds.de/admin/wb")
        (web/wait-visible {:id :xml})
        (web/js-execute "document.querySelector(\"#xml\").value = arguments[0];" xml)
        (web/click [wb-form {:tag :button :type :submit}])
        (web/wait 30))))

  (time (http/get-issues "Leder"))
  (mount/start)
  (mount/stop)

  (http/sync-with-exist "DWDS/MWA-001/der_Grosse_Teich.xml")

  (search/request "forms:plexi*")

  ws/instance)
