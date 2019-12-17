(ns zdl-lex-wikimedia.match
  (:require [clj-excel.core :as xls]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [zdl-lex-common.env :refer [env]]
            [zdl-lex-common.url :refer [path->uri]]
            [zdl-lex-corpus.cab :as cab]
            [zdl-lex-corpus.lexdb :as lexdb]
            [zdl-lex-wikimedia.index :as index]))

(def derived-forms
  #{"Alte Schreibweise"
    "Deklinierte Form"
    "Dekliniertes Gerundivum"
    "Erweiterter Infinitiv"
    "Komparativ"
    "Konjugierte Form"
    "Partizip I"
    "Partizip II"
    "Schweizer und Liechtensteiner Schreibweise"
    "Superlativ"
    "Umschrift"})

(defn basic?
  [s]
  (not (derived-forms s)))

(def pos->zdl
  {"Abkürzung" nil
   "Abkürzung (Deutsch)" nil
   "Adjektiv" "Adjektiv"
   "Adverb" "Adverb"
   "Affix" "Affix"
   "Antwortpartikel" nil
   "Artikel" nil
   "Buchstabe" nil
   "Deklinierte Form" nil
   "Dekliniertes Gerundivum" nil
   "Demonstrativpronomen" "Demonstrativpronomen"
   "Eigenname" "Eigenname"
   "Englisch" nil
   "Enklitikon" nil
   "Erweiterter Infinitiv" nil
   "Fokuspartikel" "Adverb"
   "Formel" nil
   "Gebundenes Lexem" nil
   "Geflügeltes Wort" nil
   "Gradpartikel" "Adverb"
   "Grußformel" "Ausruf"
   "Hilfsverb" nil
   "Indefinitpronomen" "Indefinitpronomen"
   "Interjektion" nil
   "International" nil
   "Interrogativadverb" "Pronominaladverb"
   "Interrogativpronomen" "Interrogativpronomen"
   "Klitikon" nil
   "Komparativ" "Komparativ"
   "Konjugierte Form" nil
   "Konjunktion"  "Konjunktion" 
   "Konjunktionaladverb" "Konjunktion"
   "Kontraktion" nil
   "Lokaladverb" "Adverb"
   "Merkspruch" nil
   "Modaladverb" "Adverb"
   "Modalpartikel" "Adverb"
   "Nachname" "Eigenname"
   "Negationspartikel" "Adverb"
   "Numerale" "Kardinalzahl"
   "Onomatopoetikum" nil
   "Ortsnamengrundwort" nil
   "Partikel" "Adverb"
   "Partizip I" nil
   "Partizip II" nil
   "Personalpronomen" "Personalpronomen"
   "Possessivpronomen" "Possessivpronomen"
   "Postposition" nil
   "Pronomen" "Pronomen"
   "Pronominaladverb" "Pronominaladverb"
   "Präfix" "Affix"
   "Präfixoid" "Affix"
   "Präposition" "Präposition"
   "Pseudopartizip" "partizipiales Adjektiv"
   "Redewendung" nil
   "Reflexivpronomen" "Reflexivpronomen"
   "Relativpronomen" "Relativpronomen"
   "Reziprokpronomen" "reziprokes Pronomen"
   "Sprichwort" nil
   "Straßenname" "Eigenname"
   "Subjunktion" "Konjunktion"
   "Substantiv" "Substantiv"
   "Suffix" "Affix"
   "Suffixoid" "Affix"
   "Superlativ" "Superlativ"
   "Temporaladverb" "Adverb"
   "Toponym" "Eigenname"
   "Verb" "Verb"
   "Vergleichspartikel" "Adverb"
   "Vorname" "Eigenname"
   "Wiederholungszahlwort" "Adverb"
   "Wortverbindung" nil
   "Zahlklassifikator" nil
   "Zahlzeichen" nil
   "Zirkumposition" nil
   "gebundenes Lexem" nil})

(defn normalize-pos [{:keys [part_of_speech collection] :as entry}]
  (condp = collection
    "de.wiktionary.org"
    (assoc entry :part_of_speech (or (get pos->zdl part_of_speech) part_of_speech))
    entry))

(defn match-entries [entries]
  (let [entries-by-form (partition-by :surface_form entries)]
    (for [entries-of-form entries-by-form
          :let [form (-> entries-of-form first :surface_form)
                entries-of-form (map normalize-pos entries-of-form)
                entries-by-collection (group-by :collection entries-of-form)
                zdl (some-> (get entries-by-collection "zdl.org") first)
                wkt (some->> (get entries-by-collection "de.wiktionary.org")
                             (filter (comp basic? :part_of_speech))
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

(defn join-corpus-freqs [mismatches]
  (let [corpora [:zeitungen :kernbasis :ibk_web_2016c]
        mismatches (partition-all 1000 mismatches)]
    (for [batch mismatches
          :let [forms (map :form batch)
                lemmata (apply cab/query-lemmata forms)
                freq-keys (map #(get lemmata % %) forms)
                freqs (->> (pmap #(lexdb/query-frequencies % freq-keys) corpora)
                           (zipmap corpora))]
          {:keys [form] :as mismatch} batch
          :let [freq-key (get lemmata form form)]]
      (assoc mismatch
             :zeitungen (get-in freqs [:zeitungen freq-key] 0)
             :kernbasis (get-in freqs [:kernbasis freq-key] 0)
             :web (get-in freqs [:ibk_web_2016c freq-key] 0)))))

(comment
  (let [mismatches (read-string (slurp (fs/file (env :data-dir) "zdl-wkt-mismatches.edn")))]
    (take 10 (drop 800 (join-corpus-freqs (take 1000 mismatches)))))

  (let [mismatches (read-string (slurp (fs/file (env :data-dir) "zdl-wkt-mismatches.edn")))]
    (spit (fs/file (env :data-dir) "zdl-wkt-mismatch-freqs.edn")
          (pr-str (join-corpus-freqs mismatches))))

  (let [mismatches (read-string (slurp (fs/file (env :data-dir) "zdl-wkt-mismatch-freqs.edn")))
        xls-header (map #(array-map :value % :underline :single)
                      ["Formangabe/Schreibung"
                       "Wiktionary-Wortklasse"
                       "Kern-Basis-Korpus"
                       "Zeitungen-Korpus"
                       "Web-Korpus"
                       "Wiktionary-Link"])
        link (fn [title base rel]
             (.. (java.net.URI. base) (resolve (path->uri rel)) (toASCIIString)))
        wkt-link (partial link "de.wiktionary.org" "https://de.wiktionary.org/wiki/")

        relevant? (fn [{:keys [kernbasis zeitungen web]}]
                    (some (partial <= 20) [kernbasis zeitungen web]))
        interesting? #(and (relevant? %))
        mismatch->row (fn [{:keys [form wkt-pos kernbasis zeitungen web] :as mm}]
                        [{:value form
                          :pattern :solid-foreground
                          :foreground-color
                          (if (interesting? mm) :light-yellow :white)}
                         (some->> wkt-pos (str/join ", "))
                         kernbasis
                         zeitungen
                         web
                         (wkt-link form)])]
    (->
     (->> (cons xls-header (map mismatch->row mismatches))
          (hash-map "Wiktionary-DE - ohne DWDS")
          (xls/build-workbook (xls/workbook-sxssf)))
     (xls/save (fs/file (env :data-dir) "zdl-wkt-mismatch-freqs.xlsx"))))

  (time
   (jdbc/with-db-transaction [c (index/db)]
     (let [wkt-only? (complement :zdl?)
           wkt-only (partial filter wkt-only?)
           records (partial map match->record)
           edn (comp #(spit (fs/file (env :data-dir) "zdl-wkt-mismatches.edn") %) pr-str)
           xls (partial matches->xls (fs/file (env :data-dir) "zdl-wkt-mismatches.xlsx"))
           result-fn (comp edn #_xls wkt-only records match-entries)]
       (index/select-entries c {} {} {:result-set-fn result-fn})))))
