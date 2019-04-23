(ns zdl-lex-server.api
  (:require [zdl-lex-server.env :refer [config]]
            [zdl-lex-server.solr :as solr]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]))

(defn form-suggestions [{{:keys [q]} :params}]
  (let [solr-response (solr/solr-suggest "forms" (or q ""))
        path-prefix [:body :suggest :forms (keyword q)]
        total (get-in solr-response (conj path-prefix :numFound) 0)
        suggestions (get-in solr-response (conj path-prefix :suggestions) [])]
    (htstatus/ok
     {:total total
      :result (for [{:keys [term payload]} suggestions]
                (merge {:suggestion term} (read-string payload)))})))

(defn status [req]
  (htstatus/ok
   {:user (get-in req [:headers "x-remote-user"] (config :anon-user))}))

(defn- facet-counts [[k v]] [k (:counts v)])

(defn- facet-values [[k v]] [k (into (sorted-map) (->> v (partition 2) (map vec)))])

(def solr-search-query
  {"facet" "true"
   "facet.field" ["author_ss" "type_ss" "pos_ss" "status_ss" "tranche_ss"]
   "facet.limit" "-1"
   "facet.mincount" "1"
   "facet.range" "timestamp_dts"
   "facet.range.start" "NOW/MONTH-1YEAR"
   "facet.range.end" "NOW"
   "facet.range.gap" "+1MONTH"
   "sort" "forms_ss asc,weight_i desc,id asc"})

(defn search [{{:keys [q offset limit]
                :or {q "id:*" offset "0" limit "10"}} :params}]
  (let [solr-search-query (merge solr-search-query
                                 {"q" q "start" offset "rows" limit})
        solr-response (solr/solr-query solr-search-query)

        {:keys [response facet_counts]} (:body solr-response)
        {:keys [numFound docs]} response
        {:keys [facet_fields facet_ranges]} facet_counts
        facets (concat (map facet-values facet_fields)
                       (map (comp facet-values facet-counts) facet_ranges))]
    (htstatus/ok
     {:total numFound
      :result (for [{:keys [abstract_ss]} docs] (-> abstract_ss first read-string))
      :facets (into (sorted-map) facets)})))
