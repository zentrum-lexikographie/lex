(ns zdl.lex.server.graph
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [zdl.lex.server.graph.db :as graph.db]
            [zdl.lex.server.graph.mantis :as graph.mantis]
            [zdl.lex.server.h2 :as h2]))

(defn get-article
  [c id]
  (first (h2/query c {:select :*
                      :from   :zdl_lex_article
                      :where  [:= :id id]})))

(defn get-anchors
  [c id]
  (h2/query c {:select :*
               :from   :zdl_lex_article_link
               :where  [:= :id id]}))

(defn get-links
  [c id]
  (h2/query c {:select  [[:this.anchor :anchor]
                         [:this.incoming :incoming]
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
                         [:<> :that.id id]]}))

(defn anchor->form
  [anchor]
  (let [sep-idx (str/last-index-of anchor "#")]
    (cond-> anchor sep-idx (subs 0 sep-idx))))

(defn handle-graph-query
  [{{{id :resource} :path} :parameters}]
  (jdbc/with-transaction [c graph.db/pool {:read-only? true}]
    (if-let [article (get-article c id)]
      {:status 200
       :body   (let [anchors (get-anchors c id)]
                 {:article article
                  :anchors anchors
                  :links   (get-links c id)
                  :issues  (let [anchors (map :anchor anchors)
                                 forms   (map anchor->form anchors)
                                 forms   (distinct forms)]
                             (graph.mantis/find-issues-by-forms forms))})}
      {:status 404
       :body   id})))

(comment
  (let [ids (h2/query graph.db/pool {:select   [[[:distinct :id] :id]]
                                     :from     :zdl_lex_article_link
                                     :order-by [:id]
                                     :limit    1000})
        ids (map :id ids)]
    (handle-graph-query {:parameters {:path {:resource (rand-nth ids)}}})))
