(ns zdl.lex.server.solr
  (:require [clojure.data.csv :as csv]
            [clojure.string :as str]
            [manifold.bus :as bus]
            [manifold.deferred :as d]
            [manifold.stream :as s]
            [mount.core :refer [defstate]]
            [zdl.lex.lucene :as lucene]
            [zdl.lex.server.article :as article]
            [zdl.lex.server.solr.client :as solr-client])
  (:import java.time.format.DateTimeFormatter
           java.time.LocalDateTime))

(defstate article-events->index
  :start (let [updates  (bus/subscribe article/events :updated)
               removals (bus/subscribe article/events :removed)
               purges (bus/subscribe article/events :purge)
               refreshs (bus/subscribe article/events :refreshed)
               updates (s/batch 10000 1000 updates)
               refreshs (s/batch 10000 10000 refreshs)]
           (s/consume-async solr-client/add-to-index updates)
           (s/consume-async solr-client/add-to-index refreshs)
           (s/consume-async solr-client/remove-from-index removals)
           (s/consume-async solr-client/remove-from-index-before purges)
           [updates removals purges refreshs])
  :stop (doseq [s article-events->index]
          (s/close! s)))

(defn- facet-values
  [[k v]]
  [(lucene/field->kw (name k))
   (into (sorted-map) (map vec (partition 2 v)))])

(defn parse-facet-response
  [{:keys [facet_fields facet_ranges facet_intervals]}]
  (into
   (sorted-map)
   (concat
    (for [kv facet_fields]
      (facet-values kv))
    (for [[k v] facet_intervals]
      [(lucene/field->kw (name k))
       (into (sorted-map) (for [[k v] v] [(name k) v]))])
    (for [[k v] facet_ranges]
      (facet-values [k (v :counts)])))))

(defn doc->abstract
  [{:keys [abstract_ss]}]
  (read-string (first abstract_ss)))

(def ^:private query-params
  {"df"   "forms_ss"
   "sort" "forms_ss asc,weight_i desc,id asc"})

(defn- ->timestamp
  [^java.time.LocalDate dt]
  (.. dt (atStartOfDay (java.time.ZoneId/of "UTC"))))

(defn- timestamp->str
  [^java.time.OffsetDateTime odt]
  (.. java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME (format odt)))

(defn- facet-params []
  (let [now        (java.time.LocalDate/now)
        today      (->timestamp now)
        year       (->timestamp (.. (java.time.Year/from now) (atDay 1)))
        tomorrow   (->timestamp (.. now (plusDays 1)))
        boundaries (concat
                    [today
                     (.. tomorrow (minusDays 7))
                     (.. tomorrow (minusMonths 1))]
                    (for [i (range 4)]
                      (.. year (minusYears i))))
        tomorrow   (timestamp->str tomorrow)
        boundaries (map timestamp->str boundaries)]
    {"facet"              "true"
     "facet.field"        ["author_s" "editors_ss"
                           "sources_ss" "tranche_ss"
                           "type_ss" "pos_ss" "status_ss"
                           "provenance_ss"
                           "errors_ss"]
     "facet.limit"        "-1"
     "facet.mincount"     "1"
     "facet.interval"     "timestamp_dt"
     "facet.interval.set" (for [b boundaries]
                            (format "{!key=\"%s\"}[%s,%s)" b b tomorrow))}))

(defn search-articles
  [{{{:keys [q offset limit] :or {q "id:*" offset 0 limit 1000}} :query} :parameters}]
  (d/chain
   (solr-client/query (assoc (merge query-params (facet-params))
                             :q (lucene/translate q)
                             :start offset
                             :rows limit))
   (fn [{{{:keys [numFound docs]} :response :keys [facet_counts]} :body}]
     {:status 200
      :body   {:total  numFound
               :result (map doc->abstract docs)
               :facets (parse-facet-response facet_counts)}})))

(defn csv-record->line
  [record]
  (with-out-str
    (csv/write-csv *out* [record])))

(def csv-header
  (csv-record->line
   ["Status" "Quelle" "Schreibung" "Definition" "Typ"
    "Ersterfassung" "Datum" "Autor" "Redakteur" "ID"]))

(defn- doc->csv
  [d]
  (let [d (doc->abstract d)]
    (csv-record->line
     [(d :status)
      (d :source)
      (some->> d :forms (str/join ", "))
      (some->> d :definitions first)
      (d :type)
      (d :provenance)
      (d :timestamp)
      (d :author)
      (d :editor)
      (d :id)])))

(defn export-article-metadata
  [{{{:keys [q limit] :or {q "id:*" limit 1000}} :query} :parameters}]
  (let [pages    (solr-client/scroll {:q (lucene/translate q)}
                                     (min (max 1 limit) 50000))
        records  (s/stream)
        ts       (.format DateTimeFormatter/ISO_DATE_TIME (LocalDateTime/now))
        filename (str "zdl-dwds-export-" ts ".csv")]
    (d/chain
     (s/put! records csv-header)
     (fn [put?]
       (when put?
         (s/connect-via pages
                        (let [n (atom 0)]
                          (fn [page]
                            (d/loop [page' page]
                              (let [doc (first page')]
                                (if (<= (swap! n inc) limit)
                                  (if doc
                                    (d/chain
                                     (s/put! records (doc->csv doc))
                                     (fn [put?]
                                       (when put?
                                         (d/recur (rest page')))))
                                    (d/success-deferred true))
                                  (do
                                    (s/close! pages)
                                    (s/close! records)
                                    (d/success-deferred false)))))))
                        records))))
    {:status 200
     :body   records
     :headers
     {"Content-Disposition" (str "attachment; filename=\"" filename "\"")
      "Content-Type"        "text/csv"}}))

(defn suggest-forms
  [{{{:keys [q]} :query} :parameters}]
  (d/chain
   (solr-client/suggest "forms" (or q ""))
   (fn [{{{:keys [forms]} :suggest} :body}]
     (let [q           (keyword q)
           total       (get-in forms [q :numFound] 0)
           suggestions (get-in forms [q :suggestions] [])]
       {:status 200
        :body   {:total  total
                 :result (for [{:keys [term payload]} suggestions]
                           (assoc (read-string payload) :suggestion term))}}))))
