(ns zdl.lex.server.graph.article
  (:require [metrics.timers :as timers]
            [next.jdbc :as jdbc]
            [zdl.lex.server.graph.db :as graph.db]
            [zdl.lex.server.h2 :as h2])
  (:import java.util.Date))

(defn delete-articles
  [c ids]
  (when (seq ids)
    (h2/execute! c {:delete-from :lex_info_form
                    :where       [:and
                                  [:in :article_id ids]
                                  [:= :article_type "zdl-lex"]]})
    (h2/execute! c {:delete-from :zdl_lex_article_link
                    :where       [:in :id ids]})
    (h2/execute! c {:delete-from :zdl_lex_article
                    :where       [:in :id ids]})))

(def update-timer
  (timers/timer ["graph" "article" "update-timer"]))

(defn update!
  [articles]
  (->>
   (jdbc/with-transaction [c graph.db/pool]
     (let [last-modified  (Date.)
           article-ids    (map :id articles)
           article-data   (for [article articles]
                          [(article :id)
                           last-modified
                           (article :status)
                           (article :type)
                           (article :pos)
                           (article :provenance)
                           (article :source)])
           incoming-links (for [article articles
                                anchor  (distinct (article :anchors))]
                            [(article :id) anchor true])
           outgoing-links (for [article articles
                                anchor  (distinct (map :anchor (article :links)))]
                            [(article :id) anchor false])
           forms          (for [article articles
                                form    (distinct (article :forms))]
                   [form (article :id) "zdl-lex"])]
       (delete-articles c article-ids)
       (when (seq article-data)
         (->>
          {:insert-into :zdl_lex_article
           :columns     [:id
                         :last_modified
                         :status
                         :type
                         :pos
                         :provenance
                         :source]
           :values      article-data}
          (h2/execute! c)))
       (when (seq incoming-links)
         (->>
          {:insert-into :zdl_lex_article_link
           :columns     [:id :anchor :incoming]
           :values      incoming-links}
          (h2/execute! c)))
       (when (seq outgoing-links)
         (->>
          {:insert-into :zdl_lex_article_link
           :columns     [:id :anchor :incoming]
           :values      outgoing-links}
          (h2/execute! c)))
       (when (seq forms)
         (->>
          {:insert-into :lex_info_form
           :columns     [:form :article_id :article_type]
           :values      forms}
          (h2/execute! c)))))
   (timers/time! update-timer)))


(defn remove!
  [article-ids]
  (jdbc/with-transaction [c graph.db/pool]
    (delete-articles c article-ids)))

(defn purge!
  [threshold]
  (jdbc/with-transaction [c graph.db/pool]
    (let [outdated (h2/query c {:select [:id]
                                :from   :zdl_lex_article
                                :where  [:< :last_modified threshold]})]
      (delete-articles c (map :id outdated)))))
