(ns zdl-lex-server.solr
  (:require [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [muuntaja.core :as m]
            [ring.util.http-response :as htstatus]
            [taoensso.timbre :as timbre]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.bus :as bus]
            [zdl-lex-common.cron :as cron]
            [zdl-lex-common.spec :as spec]
            [zdl-lex-server.auth :refer [wrap-authenticated wrap-admin-only]]
            [zdl-lex-server.csv :as csv]
            [zdl-lex-server.git :as git]
            [zdl-lex-server.solr.client :as client]
            [zdl-lex-server.solr.doc :refer [field-name->key]]
            [zdl-lex-server.solr.query :as query]))

(defn index-git-changes
  "Synchronizes modified articles with the Solr index"
  [{:keys [modified deleted]}]
  (let [article-xml-file? (article/article-xml-file? git/dir)
        file->id (article/file->id git/dir)
        modified (filter article-xml-file? modified)
        deleted (filter article-xml-file? deleted)]
    (doseq [m modified]
      (timbre/info {:solr {:modified (file->id m)}}))
    (doseq [d deleted]
      (timbre/info {:solr {:deleted (file->id d)}}))
    (try
      (client/add-articles modified)
      (client/delete-articles deleted)
      (catch Throwable t (timbre/warn t)))))

(defstate git-change-indexer
  :start (bus/listen :git-changes index-git-changes)
  :stop (git-change-indexer))

(defstate index-rebuild-scheduler
  "Synchronizes all articles with the Solr index"
  :start (cron/schedule "0 1 0 * * ?" "Solr index rebuild" client/rebuild-index)
  :stop (a/close! index-rebuild-scheduler))

(defstate index-init
  :start (try
           (when (client/index-empty?)
             (a/>!! index-rebuild-scheduler :init))
           (catch Throwable t (timbre/warn t))))

(defstate build-suggestions-scheduler
  "Synchronizes the forms suggestions with all indexed articles"
  :start (cron/schedule "0 */10 * * * ?" "Forms FSA update" client/build-forms-suggestions)
  :stop (a/close! build-suggestions-scheduler))

(defn handle-index-rebuild [_]
  (htstatus/ok {:index (a/>!! index-rebuild-scheduler :sync)}))

(defn handle-form-suggestions [{{:keys [q]} :params}]
  (let [solr-response (client/suggest "forms" (or q ""))
        path-prefix [:body :suggest :forms (keyword q)]
        total (get-in solr-response (conj path-prefix :numFound) 0)
        suggestions (get-in solr-response (conj path-prefix :suggestions) [])]
    (htstatus/ok
     {:total total
      :result (for [{:keys [term payload]} suggestions]
                (merge {:suggestion term} (read-string payload)))})))

(defn- facet-counts [[k v]]
  [k (:counts v)])

(defn- facet-values [[k v]]
  [(-> k name field-name->key)
   (into (sorted-map) (->> v (partition 2) (map vec)))])

(defn- facet-intervals [[k v]]
  [(-> k name field-name->key)
   (into (sorted-map) (for [[k v] v] [(name k) v]))])

(defn docs->results [docs]
  (for [{:keys [abstract_ss]} docs]
    (-> abstract_ss first read-string)))

(def ^:private query-params
  {"df" "forms_ss"
   "sort" "forms_ss asc,weight_i desc,id asc"})

(defn- ->timestamp
  [^java.time.LocalDate dt]
  (.. dt (atStartOfDay (java.time.ZoneId/of "UTC"))))

(defn- timestamp->str
  [^java.time.OffsetDateTime odt]
  (.. java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME (format odt)))

(defn- facet-params []
  (let [now (java.time.LocalDate/now)
        today (->timestamp now)
        year (->timestamp (.. (java.time.Year/from now) (atDay 1)))
        tomorrow (->timestamp (.. now (plusDays 1)))
        boundaries (concat
                    [today
                     (.. tomorrow (minusDays 7))
                     (.. tomorrow (minusMonths 1))]
                    (for [i (range 4)]
                      (.. year (minusYears i))))
        tomorrow (timestamp->str tomorrow)
        boundaries (map timestamp->str boundaries)]
    {"facet" "true"
     "facet.field" ["authors_ss" "editors_ss"
                    "sources_ss" "tranche_ss"
                    "type_ss" "pos_ss" "status_ss"]
     "facet.limit" "-1"
     "facet.mincount" "1"
     "facet.interval" "timestamp_dt"
     "facet.interval.set" (for [b boundaries]
                            (format "{!key=\"%s\"}[%s,%s)" b b tomorrow))}))

(comment
  (facet-params))
(defn handle-search [req]
  (let [params (-> req :parameters :query)
        {:keys [q offset limit] :or {q "id:*" offset 0 limit 1000}} params
        params {"q" (query/translate q) "start" offset "rows" limit}
        solr-response (client/query (merge query-params (facet-params) params))
        {:keys [response facet_counts]} (:body solr-response)
        {:keys [numFound docs]} response
        {:keys [facet_fields facet_ranges facet_intervals]} facet_counts
        facets (concat (map facet-values facet_fields)
                       (map facet-intervals facet_intervals)
                       (map (comp facet-values facet-counts) facet_ranges))]
    (htstatus/ok
     {:total numFound
      :result (docs->results docs)
      :facets (into (sorted-map) facets)})))

(defn- doc->csv [d]
  [(d :status)
   (d :source)
   (some->> d :forms (str/join ", "))
   (some->> d :definitions first)
   (d :type)
   (d :timestamp)
   (d :author)
   (d :editor)
   (d :id)])

(def csv-header ["Status" "Quelle" "Schreibung" "Definition" "Typ"
                 "Datum" "Autor" "Redakteur" "ID"])
(defn handle-export [req]
  (let [params (-> req :parameters :query)
        {:keys [q limit] :or {q "id:*" limit 1000}} params
        params (merge query-params {"q" (query/translate q)})
        docs (->> (client/scroll params (min limit 50000)) (take limit))
        records (->> docs docs->results (map doc->csv) (cons csv-header))
        ts (->> (java.time.LocalDateTime/now)
                (.format java.time.format.DateTimeFormatter/ISO_DATE_TIME))]
    (->
     (htstatus/ok records)
     (htstatus/update-header "Content-Disposition" str
                             "attachment; filename=\"zdl-dwds-export-" ts ".csv\""))))

(s/def ::q string?)
(s/def ::offset ::spec/pos-int)
(s/def ::limit ::spec/pos-int)
(s/def ::search-query (s/keys :opt-un [::q ::offset ::limit]))
(s/def ::export-query (s/keys :opt-un [::q ::limit]))
(s/def ::suggestion-query (s/keys :req-un [::q]))

(def ring-handlers
  ["/index"
   [""
    {:get {:summary "Query the full-text index"
           :tags ["Index" "Query"]
           :parameters {:query ::search-query}
           :handler handle-search
           :middleware [wrap-authenticated]}
     :delete {:summary "Clears the index, forcing a rebuild"
              :tags ["Index", "Admin"]
              :handler handle-index-rebuild
              :middleware [wrap-admin-only wrap-authenticated]}}]

   ["/export"
    {:get {:summary "Export index metadata in CSV format"
           :tags ["Index" "Query" "Export"]
           :parameters {:query ::export-query}
           :muuntaja (m/create (assoc m/default-options
                                      :return :output-stream
                                      :default-format "text/csv"
                                      :formats {"text/csv" csv/format}))
           :handler handle-export
           :middleware [wrap-authenticated]}}]

   ["/forms/suggestions"
    {:get {:summary "Retrieve suggestion for headwords based on prefix queries"
           :tags ["Index" "Query" "Suggestions" "Headwords"]
           :parameters {:query ::suggestion-query}
           :handler handle-form-suggestions
           :middleware [wrap-authenticated]}}]])
