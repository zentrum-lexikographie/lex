(ns zdl-lex-wikimedia.match
  (:require [clj-excel.core :as xls]
            [zdl-lex-wikimedia.index :as index]
            [zdl-lex-wikimedia.part-of-speech :as pos]
            [zdl-lex-common.article :as article]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.url :refer [path->uri]]
            [clojure.java.jdbc :as jdbc]
            [me.raynes.fs :as fs]
            [clojure.string :as str]))

(defn normalize-pos [{:keys [part_of_speech collection] :as entry}]
  (condp = collection
    "de.wiktionary.org"
    (assoc entry :part_of_speech (or (get pos/wkt->zdl part_of_speech) part_of_speech))
    entry))

(defn match-entries [entries]
  (let [entries-by-form (partition-by :surface_form entries)]
    (for [entries-of-form entries-by-form
          :let [form (-> entries-of-form first :surface_form)
                entries-of-form (map normalize-pos entries-of-form)
                entries-by-collection (group-by :collection entries-of-form)
                zdl (some-> (get entries-by-collection "zdl.org") first)
                wkt (some->> (get entries-by-collection "de.wiktionary.org")
                             (filter (comp pos/basic? :part_of_speech))
                             (first))]
          :when (or zdl wkt)]
      [form zdl wkt])))

(defn match->record [[form zdl wkt]]
  (let [[zdl wkt] (map #(some-> % :contents read-string) [zdl wkt])]
    {:form form
     :zdl? (some? zdl)
     :zdl-pos  (:pos zdl)
     :zdl-type (:type zdl)
     :zdl-status (:status zdl)
     :zdl-source (:source zdl)
     :wkt? (some? wkt)
     :wkt-pos (:pos-set wkt)
     :wkt-dwds-ref? (some-> wkt :references (contains? "Ref-DWDS"))}))

(let [xls-header (map #(array-map :value % :underline :single)
                      ["Formangabe/Schreibung"
                       "DWDS-Wortklasse"
                       "DWDS-Link"
                       "Wiktionary-Wortklasse"
                       "Wiktionary-Link"
                       "DWDS-Artikeltyp"
                       "DWDS-Artikelstatus"
                       "DWDS-Artikelquelle"
                       "Wiktionary-DWDS-Referenz"])
      link (fn [title base rel]
             (.. (java.net.URI. base) (resolve (path->uri rel)) (toASCIIString)))
      dwds-link (partial link  "dwds.de" "https://www.dwds.de/wb/")
      wkt-link (partial link "de.wiktionary.org" "https://de.wiktionary.org/wiki/")
      match->row (fn [{:keys [form
                              zdl? zdl-pos zdl-type zdl-status zdl-source
                              wkt? wkt-pos wkt-dwds-ref?]}]
                   [{:value form
                     :pattern :solid-foreground
                     :foreground-color
                     (cond
                       (and zdl? wkt? (= zdl-source "Duden_1999")) :light-orange
                       (and zdl? wkt?)  (condp = zdl-status
                                          "Red-f" :white :light-yellow)
                       zdl? :light-green
                       wkt? :dark-yellow)}
                    (some->> zdl-pos (str/join ", "))
                    (if zdl? (dwds-link form))
                    (some->> wkt-pos (str/join ", "))
                    (if wkt? (wkt-link form))
                    zdl-type
                    zdl-status
                    zdl-source
                    wkt-dwds-ref?])
      auto-size-cols (fn [wb]
                       (doseq [sheet (xls/sheets wb)
                               row (take 1 sheet)
                               column-num (range (count (xls/row-seq row)))]
                         (.. sheet (autoSizeColumn column-num)))
                       wb)]
  (defn matches->xls [xlsx-file matches]
    (->
     (->> (cons xls-header (map match->row matches))
          (hash-map "DWDS - Wiktionary-DE")
          (xls/build-workbook (xls/workbook-sxssf)))
     #_(auto-size-cols)
     (xls/save xlsx-file))))

(comment
  (time
   (jdbc/with-db-transaction [c (index/db)]
     (let [match? #(and (:zdl? %) (:wkt? %))
           only-matches (partial filter match?)
           records (partial map match->record)
           xls (partial matches->xls (fs/file (env :data-dir) "zdl-wkt-matches-1.xlsx"))
           result-fn (comp xls records match-entries)]
       (index/select-entries c {} {} {:result-set-fn result-fn})))))
