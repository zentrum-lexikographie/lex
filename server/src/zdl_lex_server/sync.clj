(ns zdl-lex-server.sync
  (:require [clojure.core.async :as async]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]
            [ring.util.http-response :as htstatus]
            [zdl-lex-server.status :as status]))

(defstate index->suggestions
  "Synchronizes the forms suggestions with all indexed articles"
  :start (let [ch (async/chan (async/sliding-buffer 1))
               interval (config :solr-sync-interval)]
           (async/go-loop []
             (when (async/alt! (async/timeout interval) :tick ch ([v] v))
               (async/<!
                (async/thread
                  (try
                    (solr/build-suggestions "forms")
                    (catch Throwable t))))
               (async/poll! ch) ;; we just finished a sync; remove pending reqs
               (recur)))
           ch)
  :stop (async/close! index->suggestions))

(defstate git-changes->solr
  "Synchronizes modified articles with the Solr index"
  :start (let [stop-ch (async/chan)]
           (async/go-loop []
             (when-let [changes (async/alt! stop-ch nil git/changes ([v] v))]
               (let [articles (filter store/article-file? changes)
                     modified (filter fs/exists? articles)
                     deleted (remove fs/exists? articles)]
                 (solr/add-articles modified)
                 (solr/delete-articles deleted)
                 (async/>! index->suggestions :sync))
               (recur)))
           stop-ch)
  :stop (async/close! git-changes->solr))

(defstate git-all->solr
  "Synchronizes all articles with the Solr index"
  :start (let [ch (async/chan (async/sliding-buffer 1))
               interval (config :solr-sync-interval)]
           (async/go-loop []
             (when (async/alt! (async/timeout interval) :tick ch ([v] v))
               (async/<!
                (async/thread (try (solr/sync-articles) (catch Throwable t))))
               (async/>! index->suggestions :sync)
               (async/poll! ch) ;; we just finished a sync; remove pending reqs
               (recur)))
           ch)
  :stop (async/close! git-all->solr))


(defn handle-index-trigger [req]
  (if (= "admin" (status/user req))
    (htstatus/ok
     {:index (async/>!! git-all->solr :sync)})
    (htstatus/forbidden
     {:index false})))
