(ns zdl.lex.server.index
  (:require
   [clj-http.client :as http]
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [gremid.xml :as gx]
   [jsonista.core :as json]
   [lambdaisland.uri :as uri]
   [ring.util.io :as rio]
   [ring.util.response :as resp]
   [taoensso.telemere :as tm]
   [tick.core :as t]
   [zdl.lex.article :as article]
   [zdl.lex.env :as env]
   [zdl.lex.lucene :as lucene]
   [zdl.lex.server.qa :as qa]))

(def query-request
  {:method :post
   :url    (str (uri/join env/solr-url "query"))})

(def query-timer
  (env/timer "index.query"))

(defn query
  [query-params]
  (with-open [_ (env/timed! query-timer)]
   (-> query-request
       (assoc :form-params query-params)
       (http/request)
       (update :body json/read-value))))

(def update-request
  {:request-method :post
   :url            (str (uri/join env/solr-url "update"))
   :query-params   {"wt" "json"}
   :headers        {"Content-Type" "text/xml"}})

(def update-timer
  (env/timer "index.update"))

(defn update!
  [xml-node]
  (with-open [_ (env/timed! update-timer)]
   (->
    update-request
    (assoc :body (with-out-str (gx/write-node *out* (gx/sexp->node xml-node))))
    (http/request))))

(defn add!
  [docs]
  (->> (partition-all 10000 docs)
       (map (fn [batch] [:add (seq batch)]))
       (run! update!)))

(defn remove!
  [ids]
  (->> (partition-all 10000 ids)
       (map (fn [batch] [:delete (for [id batch] [:id id])]))
       (run! update!)))

(defn optimize!
  []
  (update! [:update [:commit] [:optimize]]))

(defn purge!
  [doc-type threshold]
  (let [query (format "doc_type_s:%s && time_l:[* TO %s}" doc-type threshold)]
    (update! [:delete [:query query]])))

(defn clear!
  [doc-type]
  (update! [:delete [:query (format "doc_type_s:%s" doc-type)]]))

;; # Fields

(def article-abstract-fields
  "Solr fields which comprise the document abstract/summary."
  [:id :type :status :provenance
   :last-modified :timestamp
   :author :editor :source
   :forms :pos :definitions
   :errors])

(defn article->fields
  "Returns Solr fields/values for a given article."
  [{:keys [id] :as article}]
  {"id"                  id
   "language"            "de"
   "doc_type_s"          "article"
   "time_l"              (str (System/currentTimeMillis))
   "xml_descendent_path" id
   "abstract_s"          (pr-str (select-keys article article-abstract-fields))
   "type_s"              (article :type)
   "status_s"            (article :status)
   "source_s"            (article :source)
   "author_s"            (article :author)
   "editor_s"            (article :editor)
   "timestamp_dt"        (article :timestamp)
   "last_modified_dt"    (article :last-modified)
   "tranche_s"           (article :tranche)
   "provencance_s"       (article :provenance)
   "form_s"              (first (article :forms))
   "forms_ss"            (article :forms)
   "forms_txt"           (article :forms)
   "pos_s"               (article :pos)
   "gender_ss"           (article :gender)
   "definitions_txt"     (article :definitions)
   "errors_ss"           (article :errors)
   "anchors_ss"          (article :anchors)
   "links_ss"            (map :anchor (article :links))})

(def issue-abstract-fields
  [:id :form :updated :summary :status :category :severity :resolution])

(defn issue->fields
  [issue]
  {"id"           (issue :id)
   "language"     "de"
   "doc_type_s"   "issue"
   "time_l"       (str (System/currentTimeMillis))
   "abstract_s"   (pr-str (select-keys issue issue-abstract-fields))
   "form_s"       (issue :form)
   "updated_s"    (issue :updated)
   "summary_s"    (issue :summary)
   "category_s"   (issue :category)
   "status_s"     (issue :status)
   "severity_s"   (issue :severity)
   "reporter_s"   (issue :reporter)
   "handler_s"    (issue :handler)
   "resolution_s" (issue :resolution)})

(defn fields->doc
  [fields]
  [:doc
   (for [[k vs] fields                              :when (some? vs)
         v      (cond-> vs (not (coll? vs)) (list)) :when (some? v)]
     [:field {:name k} v])])

(def article->doc
  (comp fields->doc article->fields))

(defn parse-article-file
  [{:keys [file id] :as desc}]
  (tm/with-ctx+ {::id id}
    (try
      (let [xml     (article/read-xml file)
            article (article/metadata xml)
            errors  (qa/check-for-errors xml file)]
        (assoc (merge desc article errors) :xml xml))
      (catch Throwable t (tm/error! t) desc))))

(defn upsert-articles!
  [descs]
  (add! (pmap (comp article->doc parse-article-file) descs)))

(def issue->doc
  (comp fields->doc issue->fields))

(defn doc->abstract
  [{abstract "abstract_s"}]
  (read-string abstract))

;; # Query Articles

(defn timestamp-facet-intervals
  []
  (let [today  (t/offset-date-time (. (t/date) (atStartOfDay (t/zone "UTC"))))
        end    (t/>> today (t/of-days 1))
        week   (t/<< end (t/of-days 7))
        month  (t/<< end (t/of-months 1))
        year   (-> (t/truncate today :days)
                       (t/with :month-of-year 1)
                       (t/with :day-of-month 1))
        years  (map #(t/<< year (t/of-years %)) (range 4))
        starts (concat (list today week month) years)]
    (into [] (map #(format "{!key=\"%s\"}[%s,%s)"
                           (t/date %) (t/instant %) (t/instant end))) starts)))

(def facet-key
  {"author_s"     :author
   "editor_s"     :editor
   "errors_ss"    :errors
   "pos_s"        :pos
   "provenance_s" :provenance
   "source_s"     :source
   "status_s"     :status
   "timestamp_dt" :timestamp
   "tranche_s"    :tranche
   "type_s"       :type})

(defn facet-values
  [[k v]]
  [(facet-key k) (into (sorted-map) (map vec (partition 2 v)))])

(defn parse-article-facet-response
  [{fields "facet_fields" ranges "facet_ranges" intervals "facet_intervals"}]
  (into
   (sorted-map)
   (concat
    (for [kv fields] (facet-values kv))
    (for [[k v] intervals] [(facet-key k) (into (sorted-map) (for [[k v] v] [k v]))])
    (for [[k v] ranges] (facet-values [k (get v "counts")])))))

(defn parse-article-query-response
  [{{{total "numFound" docs "docs"} "response" facets "facet_counts"} :body}]
  {:total  total
   :result (into [] (map doc->abstract) docs)
   :facets (parse-article-facet-response facets)})

(defn handle-article-query
  [{{{:keys [q offset limit]} :query} :parameters}]
  (->>
   {"q"                  (lucene/roundtrip (or q "id:*"))
    "fq"                 "doc_type_s:article"
    "start"              (str (or offset 0))
    "rows"               (str (or limit 1000))
    "df"                 "forms_ss"
    "fl"                 "abstract_s"
    "sort"               "forms_ss asc,last_modified_dt desc,id asc"
    "wt"                 "json"
    "facet"              "true"
    "facet.field"        ["author_s" "editor_s"
                          "source_s" "tranche_s"
                          "type_s" "status_s" "pos_s"
                          "provenance_s" "errors_ss"]
    "facet.limit"        "-1"
    "facet.mincount"     "1"
    "facet.interval"     "timestamp_dt"
    "facet.interval.set" (timestamp-facet-intervals)}
   (query)
   (parse-article-query-response)
   (resp/response)))

(defn assoc-hl
  [hl {:keys [id] :as result}]
  (let [[snippet] (get-in hl [id "forms_txt"])]
    (cond-> result snippet (assoc :snippet snippet))))


(defn parse-article-suggest-response
  [{{{total "numFound" docs "docs"} "response" hl "highlighting"} :body}]
  {:total total
   :result (into [] (map (comp (partial assoc-hl hl) doc->abstract)) docs)})

(defn handle-article-suggest
  [{{{:keys [q]} :query} :parameters}]
  (->>
   {"q"           (lucene/->str
                   [:match (str (lucene/escape-pattern (or q "")) "*")])
    "fq"          "doc_type_s:article"
    "df"          "forms_txt"
    "fl"          "abstract_s"
    "sort"        "score desc,last_modified_dt desc,id asc"
    "hl"          "true"
    "hl.fl"       "forms_txt"
    "hl.fragsize" "0"
    "hl.tag.pre"  "<b>"
    "hl.tag.post" "</b>"
    "hl.encoder"  "html"
    "wt"          "json"}
   (query)
   (parse-article-suggest-response)
   (resp/response)))

;; # Links

(defn str->set
  [v]
  (some->> (if (string? v) [v] v) (into (sorted-set))))

(defn value->phrase
  [v]
  [:phr (lucene/escape-phrase v)])

(defn set->field-query
  [field vs]
  [:c
   [:field field]
   [:sub (into [:q] (interpose [:or] (map value->phrase vs)))]])

(defn parse-links-response
  [{{{total "numFound" docs "docs"} "response"} :body}]
  {:total  total
   :result (for [{anchors "anchors_ss" links "links_ss" :as doc} docs]
             (assoc (doc->abstract doc) :anchors anchors :links links))})

(def links-query-params
  {"fq"   "doc_type_s:article"
   "fl"   "abstract_s,anchors_ss,links_ss"
   "rows" "1000"
   "wt"   "json"})

(defn handle-links-query
  [{{{:keys [anchors links]} :query} :parameters}]
  (let [anchors (str->set anchors)
        links   (str->set links)]
    (if (and (empty? anchors) (empty? links))
      (resp/not-found "Links")
      (->>
       (into
        [:q]
        (interpose
         [:or]
         (cond-> []
           anchors (conj (set->field-query "anchors_ss" anchors))
           links   (conj (set->field-query "links_ss" links)))))
       (lucene/->str)
       (assoc links-query-params "q")
       (query)
       (parse-links-response)
       (resp/response)))))

;; # CSV Export

(def csv-header
  ["Status" "Quelle" "Schreibung" "Definition" "Typ"
   "Ersterfassung" "Datum" "Autor" "Redakteur" "ID"])

(defn doc->csv
  [d]
  (let [d (doc->abstract d)]
    [(d :status)
     (d :source)
     (some->> d :forms (str/join ", "))
     (some->> d :definitions first)
     (d :type)
     (d :provenance)
     (d :timestamp)
     (d :author)
     (d :editor)
     (d :id)]))

(defn scroll
  ([params]
   (scroll params 20000))
  ([params page-size]
   (scroll params page-size 0))
  ([params page-size page]
   (let [resp (query (assoc params "start" (* page page-size) "rows" page-size))
         docs (get-in resp [:body "response" "docs"] [])]
     (when (seq docs)
       (lazy-cat docs (scroll params page-size (inc page)))))))

(defn write-csv
  [records out]
  (with-open [w (io/writer out :encoding "UTF-8")]
    (csv/write-csv w (cons csv-header records))))

(def export-query-params
  {"df"   "forms_ss"
   "fq"   "doc_type_s:article"
   "sort" "forms_ss asc,last_modified_dt desc,id asc"})

(defn handle-export
  [{{{:keys [q limit] :or {q "id:*" limit 1000}} :query} :parameters}]
  (let [params    (assoc export-query-params "q" (lucene/roundtrip q))
        page-size (min (max 1 limit) 50000)
        docs      (scroll params page-size)
        ts        (str (t/truncate (t/date-time) :seconds))
        filename  (str "zdl-dwds-export-" ts ".csv")]
    (-> (partial write-csv (map doc->csv (take limit docs)))
        (rio/piped-input-stream)
        (resp/response)
        (resp/header "Content-Type" "text/csv")
        (resp/header "Content-Disposition"
                     (str "attachment; filename=\"" filename "\"")))))

;; # Query Issues

(defn parse-issue-response
  [{{{total "numFound" docs "docs"} "response"} :body}]
  {:total  total
   :result (into [] (map doc->abstract) docs)})

(def issue-query-params
  {"fq"   "doc_type_s:issue"
   "fl"   "abstract_s"
   "rows" "1000"})

(defn handle-issue-query
  [{{{:keys [q]} :query} :parameters}]
  (let [vs (some->> (if (string? q) [q] q) (into (sorted-set)))]
    (->>
     [:q
      [:c
       [:field "form_s"]
       [:sub (into [:q] (interpose [:or] (map value->phrase vs)))]]]
     (lucene/->str)
     (assoc issue-query-params "q")
     (query)
     (parse-issue-response)
     (resp/response))))
