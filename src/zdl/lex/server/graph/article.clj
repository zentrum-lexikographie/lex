(ns zdl.lex.server.graph.article
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [manifold.bus :as bus]
            [manifold.stream :as s]
            [mount.core :as mount :refer [defstate]]
            [next.jdbc :as jdbc]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.graph :as graph]
            [zdl.lex.server.graph.mantis :as mantis]
            [zdl.lex.server.h2 :as h2])
  (:import java.util.Date))

(defn delete-article
  [c id]
  (h2/execute! c {:delete-from :lex_info_form
                  :where       [:and
                                [:= :article_id id]
                                [:= :article_type "zdl-lex"]]})
  (h2/execute! c {:delete-from :zdl_lex_article_link
                  :where       [:= :id id]})
  (h2/execute! c {:delete-from :zdl_lex_article
                  :where       [:= :id id]}))

(defn article->db
  [c last-modified {:keys [id] :as article}]
  (delete-article c id)
  (h2/execute! c {:insert-into :zdl_lex_article
                  :columns     [:id :last_modified
                                :status :type :pos
                                :provenance :source]
                  :values      [[id last-modified
                            (article :status) (article :type) (article :pos)
                            (article :provenance) (article :source)]]})
  (doseq [anchor (distinct (article :anchors))]
    (h2/execute! c {:insert-into :zdl_lex_article_link
                    :columns     [:id :anchor :incoming]
                    :values      [[id anchor true]]}))
  (doseq [anchor (distinct (map :anchor (article :links)))]
    (h2/execute! c {:insert-into :zdl_lex_article_link
                    :columns     [:id :anchor :incoming]
                    :values      [[id anchor false]]}))
  (doseq [form (distinct (article :forms))]
    (h2/execute! c {:insert-into :lex_info_form
                    :columns     [:form :article_id :article_type]
                    :values      [[form id "zdl-lex"]]})))

(defn add-to-graph
  [articles]
  (graph/transact!
   (fn [c]
     (let [now (Date.)]
       (log/debugf "Adding %d articles to graph" (count articles))
       (doseq [article articles]
         (article->db c now article))))))

(defn remove-from-graph
  [articles]
  (graph/transact!
   (fn [c]
     (log/infof "Removing %d article(s) from graph" (count articles))
     (doseq [{:keys [id]} articles]
       (delete-article c id)))))

(defn remove-from-graph-before
  [threshold]
  (graph/transact!
   (fn [c]
     (log/infof "Purging articles before %s" threshold)
     (->> (h2/query c {:select [:id]
                       :from   :zdl_lex_article
                       :where  [:< :last_modified threshold]})
          (map #(delete-article c (:id %)))
          (dorun)))))

(defstate article-events->graph
  :start (let [updates  (bus/subscribe article/events :updated)
               removals (bus/subscribe article/events :removed)
               purges   (bus/subscribe article/events :purge)
               refreshs (bus/subscribe article/events :refreshed)
               updates  (s/batch 10000 1000 updates)
               refreshs (s/batch 10000 10000 refreshs)]
           (s/consume-async add-to-graph updates)
           (s/consume-async add-to-graph refreshs)
           (s/consume-async remove-from-graph removals)
           (s/consume-async remove-from-graph-before purges)
           [updates removals purges refreshs])
  :stop (doseq [s article-events->graph]
          (s/close! s)))

(defn anchor->form
  [anchor]
  (let [sep-idx (str/last-index-of anchor "#")]
    (if sep-idx (subs anchor 0 sep-idx) anchor)))

(defn anchor->id
  ([{:keys [anchor incoming]}]
   (anchor->id anchor incoming))
  ([anchor incoming]
   (let [sep-idx (str/last-index-of anchor "#")]
     (str (if sep-idx anchor (str anchor "#1"))
          (if incoming "<" ">")))))

(defn get-article-graph
  [{{{id :resource} :path} :parameters}]
  (jdbc/with-transaction [c graph/db {:read-only? true}]
    (if-let [article (first (h2/query c {:select :*
                                         :from   :zdl_lex_article
                                         :where  [:= :id id]}))]
      {:status 200
       :body
       (let [anchors (h2/query c {:select :*
                                  :from   :zdl_lex_article_link
                                  :where  [:= :id id]})
             anchors (map #(assoc % :id (anchor->id %)) anchors)
             anchors (map #(assoc % :form (-> % :anchor anchor->form)) anchors)
             forms   (distinct (map :form anchors))
             links   (h2/query c {:select  [[:this.anchor :this_anchor]
                                            [:this.incoming :this_incoming]
                                            [:that_article.*]]
                                  :from    [[:zdl_lex_article_link :this]]
                                  :join-by [:join [[:zdl_lex_article_link :that]
                                                   [[:and
                                                     [:= :this.anchor :that.anchor]
                                                     [:<> :this.incoming :that.incoming]]]]
                                            :join [[:zdl_lex_article :that_article]
                                                   [:= :that.id :that_article.id]]]
                                  :where   [:and
                                            [:= :this.id id]
                                            [:<> :that.id id]]})
             issues  (mantis/find-issues-by-forms forms)]
         {:nodes
          (concat
           (list
            [id (assoc article :type :zdl-lex-article)])
           (for [anchor anchors]
             [(:id anchor) (assoc (dissoc anchor :id :form) :type :anchor)])
           (for [form forms]
             [form {:type :form :form form}])
           (for [link links]
             [(:id link) (dissoc link :this-anchor :this-incoming)])
           (for [issue issues]
             [(:url issue) (assoc issue :type :issue)]))
          :edges
          (concat
           (for [anchor anchors]
             [id (:id anchor)])
           (for [anchor anchors]
             [(:id anchor) (:form anchor)])
           (for [issue issues]
             [(:form issue) (:url issue)])
           (for [{:keys [this-anchor this-incoming id]} links]
             [(anchor->id this-anchor this-incoming) id]))})}
      {:status 404
       :body   id})))
