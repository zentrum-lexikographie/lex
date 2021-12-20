(ns zdl.lex.server.solr.query
  "Facetted queries of articles."
  (:require [clojure.core.async :as a]
            [zdl.lex.lucene :as lucene]
            [zdl.lex.server.solr.client :as solr.client]
            [zdl.lex.server.solr.fields :as solr.fields])
  (:import [java.time LocalDate OffsetDateTime Year ZoneId]
           java.time.format.DateTimeFormatter))

;; # Query construction

(def utc-zone
  (ZoneId/of "UTC"))

(defn date->timestamp
  [^LocalDate dt]
  (.atStartOfDay dt utc-zone))

(defn timestamp->str
  [^OffsetDateTime odt]
  (.format DateTimeFormatter/ISO_OFFSET_DATE_TIME odt))

(defn timestamp-facet-intervals
  []
  (let [now        (LocalDate/now)
        today      (date->timestamp now)
        tomorrow   (date->timestamp (.plusDays now 1))
        week       (.minusDays tomorrow 7)
        month      (.minusMonths tomorrow 1)
        year       (date->timestamp (.atDay (Year/from now) 1))
        years      (map #(.minusYears year %) (range 4))
        boundaries (concat [today week month] years)
        starts     (map timestamp->str boundaries)
        end        (timestamp->str tomorrow)]
    (for [start starts]
      (format "{!key=\"%s\"}[%s,%s)" start start end))))

(defn request->query
  [req]
  (let [query  (get-in req [:parameters :query])
        q      (get query :q "id:*")
        offset (get query :offset 0)
        limit  (get query :limit 1000)]
    {"q"                  (lucene/translate q)
     "fq"                 "doc_type:article"
     "start"              (str offset)
     "rows"               (str limit)
     "df"                 "forms_ss"
     "fl"                 "abstract_ss"
     "sort"               "forms_ss asc,weight_i desc,id asc"
     "facet"              "true"
     "facet.field"        ["author_s" "editor_s"
                           "source_s" "tranche_ss"
                           "type_s" "status_s" "pos_ss"
                           "provenance_s" "errors_ss"]
     "facet.limit"        "-1"
     "facet.mincount"     "1"
     "facet.interval"     "timestamp_dt"
     "facet.interval.set" (timestamp-facet-intervals)}))

;; # Query result handling

(defn- facet-values
  [[k v]]
  [(lucene/field-name->key (name k))
   (into (sorted-map) (map vec (partition 2 v)))])

(defn parse-facet-response
  [{:keys [facet_fields facet_ranges facet_intervals]}]
  (into
   (sorted-map)
   (concat
    (for [kv facet_fields]
      (facet-values kv))
    (for [[k v] facet_intervals]
      [(lucene/field-name->key (name k))
       (into (sorted-map) (for [[k v] v] [(name k) v]))])
    (for [[k v] facet_ranges]
      (facet-values [k (v :counts)])))))

(defn parse-response
  [{{{:keys [numFound docs]} :response :keys [facet_counts]} :body}]
  {:total  numFound
   :result (map solr.fields/doc->abstract docs)
   :facets (parse-facet-response facet_counts)})

;; # Query

(defn handle-query
  [req]
  (a/go
    (if-let [response (a/<! (solr.client/query (request->query req)))]
      {:status 200
       :body (parse-response response)}
      {:status 502})))

