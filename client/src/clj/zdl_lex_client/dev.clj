(ns zdl-lex-client.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [zdl-lex-client.editors :as editors]
            [zdl-lex-client.status :as status]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.results :as results]))

(comment
  @workspace/instance
  (mount/start)
  @status/current
  status/label
  (-> editors/listeners :editors deref)
  (search/new-query "forms:plexi*")
  results/output
  (let [panel (ui/border-panel :north search/input :center results/output)]
    (ui/invoke-later
     (-> (ui/frame :title "Search" :content panel :size [800 :by 600])
         ui/show!)))
  (mount/stop))
