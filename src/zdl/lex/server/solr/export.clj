(ns zdl.lex.server.solr.export
  (:require [clojure.core.async :as a]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.util.io :as rio]
            [zdl.lex.lucene :as lucene]
            [zdl.lex.server.solr.client :as solr.client]
            [zdl.lex.server.solr.fields :as solr.fields])
  (:import java.time.format.DateTimeFormatter
           java.time.LocalDateTime))

(def csv-header
  ["Status" "Quelle" "Schreibung" "Definition" "Typ"
   "Ersterfassung" "Datum" "Autor" "Redakteur" "ID"])

(defn- doc->csv
  [d]
  (let [d (solr.fields/doc->abstract d)]
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

(defn csv-content-disposition
  []
  (str "attachment; filename=\"zdl-dwds-export-"
       (.format DateTimeFormatter/ISO_DATE_TIME (LocalDateTime/now))
       ".csv\""))

(defn scroll
  ([params]
   (scroll params 20000))
  ([params page-size]
   (let [pages (a/chan)]
     (a/go-loop [page 0]
       (let [request  (assoc params :start (* page page-size) :rows page-size)
             response (a/<! (solr.client/query request))
             docs     (get-in response [:body :response :docs] [])]
         (if (and (seq docs) (a/>! pages docs) (>= (count docs) page-size))
           (recur (inc page))
           (a/close! pages))))
     pages)))

(defn stream-results
  [pages limit out]
  (with-open [w (io/writer out :encoding "UTF-8")]
    (a/<!!
     (a/go-loop [limit' limit]
       (when-let [docs (a/<! pages)]
         (when (= limit' limit)
           (csv/write-csv w [csv-header]))
         (let [page    (map doc->csv (take limit' docs))
               limit'' (- limit' (count page))]
           (csv/write-csv w page)
           (when (> limit'' 0)
             (recur limit''))))))))

(defn handle-export
  [{{{:keys [q limit] :or {q "id:*" limit 1000}} :query} :parameters}]
  (let [request   {:q    (lucene/translate q)
                   :df   "forms_ss"
                   :fq   "doc_type:article"
                   :sort "forms_ss asc,weight_i desc,id asc"}
        page-size (min (max 1 limit) 50000)
        pages     (scroll request page-size)]
    {:status  200
     :headers {"Content-Disposition" (csv-content-disposition)
               "Content-Type"        "text/csv"}
     :body    (rio/piped-input-stream (partial stream-results pages limit))}))
