(ns zdl-lex-server.sync
  (:require [clojure.core.async :as async]
            [me.raynes.fs :as fs]
            [mount.core :refer [defstate]]
            [zdl-lex-server.cron :as cron]
            [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.solr :as solr]
            [zdl-lex-server.store :as store]
            [ring.util.http-response :as htstatus]
            [zdl-lex-server.status :as status]
            [taoensso.timbre :as timbre]))

(defstate index->suggestions
  "Synchronizes the forms suggestions with all indexed articles"
  :start (let [schedule (cron/parse "0 */10 * * * ?")
               ch (async/chan (async/sliding-buffer 1))]
           (async/go-loop []
             (when (async/alt! ch ([v] v)
                               (async/timeout (cron/millis-to-next schedule)) :tick)
               (timbre/info {:solr :build-suggestions})
               (async/<!
                (async/thread
                  (try
                    (solr/build-suggestions "forms")
                    (catch Throwable t (timbre/warn t)))))
               (async/poll! ch) ;; we just finished a sync; remove pending req
               (recur)))
           ch)
  :stop (async/close! index->suggestions))

(defstate git-changes->solr
  "Synchronizes modified articles with the Solr index"
  :start (let [stop-ch (async/chan)]
           (async/go-loop []
             (when-let [changes (async/alt! git/changes ([v] v) stop-ch nil)]
               (let [articles (filter store/article-file? changes)
                     modified (filter fs/exists? articles)
                     deleted (remove fs/exists? articles)]
                 (doseq [m modified] (timbre/info {:solr {:modified (store/file->id m)}}))
                 (doseq [d deleted] (timbre/info {:solr {:deleted (store/file->id d)}}))
                 (when (async/<!
                        (async/thread
                          (try
                            (solr/add-articles modified)
                            (solr/delete-articles deleted)
                            (catch Throwable t (timbre/warn t)))))))
               (recur)))
           stop-ch)
  :stop (async/close! git-changes->solr))

(defstate git-all->solr
  "Synchronizes all articles with the Solr index"
  :start (let [schedule (cron/parse "0 1 0 * * ?")
               ch (async/chan (async/sliding-buffer 1))]
           (async/go-loop []
             (when (async/alt! (async/timeout (cron/millis-to-next schedule)) :tick
                               ch ([v] v))
               (timbre/info {:solr :sync})
               (when (async/<!
                      (async/thread (try (solr/sync-articles) (catch Throwable t)))))
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
