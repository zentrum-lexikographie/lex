(ns zdl-lex-client.dev
  (:require [mount.core :as mount]
            [seesaw.core :as ui]
            [zdl-lex-client.editors :as editors]
            [zdl-lex-client.status :as status]
            [zdl-lex-client.workspace :as workspace]
            [zdl-lex-client.search :as search]
            [zdl-lex-client.results :as results]
            [zdl-lex-client.http :as http]
            [zdl-lex-schema.validate :as validate]
            [clojure.core.async :as async]
            [clojure.java.io :as io]))

(comment
  workspace/instance
  (mount/start)
  @status/current
  status/label
  (-> editors/listeners :editors deref)
  @editors/active
  (search/new-query "forms:plexi*")
  results/output
  (validate/validate-dirs "../data/git/articles/Neuartikel")
  (http/post-edn #(merge % {:path "/articles/exist/sync-id"
                            :query {"id" "DWDS/MWA-001/der_Grosse_Teich.xml"}})
                 {})
  (async/>!! editors/save-events
             "http://spock.dwds.de:8080/exist/webdav/db/dwdswb/data/DWDS/MWA-001/der_Grosse_Teich.xml")
  (let [panel (ui/border-panel :north search/input :center results/output)]
    (ui/invoke-later
     (-> (ui/frame :title "Search" :content panel :size [800 :by 600])
         ui/show!)))
  (mount/stop))
