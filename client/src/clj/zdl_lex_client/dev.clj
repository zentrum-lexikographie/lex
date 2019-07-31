(ns zdl-lex-client.dev
  (:require [clojure.core.async :as async]
            [mount.core :as mount]
            [seesaw.core :as ui]
            [taoensso.timbre :as timbre]
            [zdl-lex-client.editors :as editors]
            [zdl-lex-client.http :as http]
            [zdl-lex-client.results :as results]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.status :as status]
            [zdl-lex-client.workspace :as workspace])
  (:import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace))

(defn show-search-testbed []
  (let [ws (proxy [StandalonePluginWorkspace] []
             (open [url]
               (->
                (ui/dialog :content (str url)
                           :modal? true
                           :success-fn #(ui/dispose! %)
                           :cancel-fn #(ui/dispose! %))
                (ui/pack!)
                (ui/show!))
               true)
             (showView [id request-focus?]
               (timbre/info {:id id :request-focus? request-focus?}))
             (addEditorChangeListener [_ _])
             (removeEditorChangeListener [_ _]))]
    (mount/stop)
    (mount/start-with {#'zdl-lex-client.workspace/instance ws})
    (ui/invoke-later
     (ui/show!
      (ui/frame
       :title "Search"
       :size [800 :by 600]
       :content (ui/border-panel :north search/input
                                 :center results/tabbed-pane))))))

(comment
  workspace/instance
  (mount/start)
  (show-search-testbed)
  @status/current
  status/label
  (-> editors/listeners :editors deref)
  @editors/active
  (search/new-query "forms:plexi*")
  results/tabbed-pane
  (http/post-edn #(merge % {:path "/articles/exist/sync-id"
                            :query {"id" "DWDS/MWA-001/der_Grosse_Teich.xml"}})
                 {})
  (async/>!! editors/save-events
             "http://spock.dwds.de:8080/exist/webdav/db/dwdswb/data/DWDS/MWA-001/der_Grosse_Teich.xml")
  (mount/stop))
