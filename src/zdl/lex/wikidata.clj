(ns zdl.lex.wikidata
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.java.process :as p]
   [clojure.string :as str]
   [jsonista.core :as json]
   [julesratte.client :as jr]
   [medley.core :refer [update-existing]]
   [org.httpkit.client :as hc]
   [pg.core :as pg]
   [ring.util.io :as ring.io]
   [taoensso.telemere :as tel]
   [zdl.lex.db :as db]
   [zdl.lex.env :refer [getenv]]
   [zdl.lex.util :refer [pr-edn-str]])
  (:import
   (java.time LocalDate)
   (java.util.zip GZIPInputStream)
   (org.apache.commons.compress.compressors.xz XZCompressorInputStream)))

(tel/set-min-level! nil "julesratte.*" :warn)

(defn valid-wikidata-lemma?
  [s]
  (re-seq #"^[0-9a-zA-ZÄÉÖÜßàáâãäåçèéêîñóôöøùúûüŒœř\₀\₂'…\!\,\-\.\?\ ]+$" s))

(def lex-cat->pos
  {"Q34698"  "ADJ"
   "Q380057" "ADV"
   "Q1084"   "NN"
   "Q24905"  "V"})

(def pos->lex-cat
  (reduce-kv (fn [m lex-cat pos] (assoc m pos lex-cat)) {} lex-cat->pos))

(def pos-of-interest?
  (into #{} (vals lex-cat->pos)))

(def gender->entity-id
  {"Masc" "Q499327"
   "Fem"  "Q1775415"
   "Neut" "Q1775461"})

(def grammatical-features
  [["Q179230"    "infinitive"]
   ["Q100952920" "zu infinitive"]
   ["Q1317831"   "active"]
   ["Q682111"    "indicative"]
   ["Q55685962"  "subjunctive I"]
   ["Q54671845"  "subjunctive II"]
   ["Q192613"    "present"]
   ["Q442485"    "preterite"]
   ["Q110786"    "singular"]
   ["Q146786"    "plural"]
   ["Q131105"    "nominative"]
   ["Q146233"    "genitive"]
   ["Q145599"    "dative"]
   ["Q146078"    "accusative"]
   ["Q3482678"   "positive"]
   ["Q14169499"  "comparative"]
   ["Q1817208"   "superlative"]
   ["Q21714344"  "first person"]
   ["Q51929049"  "second person"]
   ["Q51929074"  "third person"]
   ["Q22716"     "imperative"]
   ["Q10345583"  "present participle"]
   ["Q12717679"  "past participle"]
   ["Q1931259"   "predicative"]])

(def grammatical-feature-labels
  (into {} grammatical-features))

(def grammatical-feature-index
  (into {} (map-indexed (fn [i [qid _]] [qid i]) grammatical-features)))

(def num-grammatical-features
  (count grammatical-features))

(def dump-url
  "https://dumps.wikimedia.org/wikidatawiki/entities/latest-lexemes.json.gz")

(defn dump-reader
  "Download latest lexeme dump via curl.

  JDK's HTTP client fails to download the resource completely."
  []
  (->
   (p/start {:err :discard} "curl" "-s" dump-url)
   (p/stdout)
   (io/input-stream) (GZIPInputStream.) (io/reader)))

(defn parse-dump
  [reader]
  (sequence
   (comp
    (map str/trim)
    (filter #(< 2 (count %)))
    (map #(str/replace % #",$" ""))
    (partition-all 32)
    (mapcat (partial pmap json/read-value)))
   (line-seq reader)))

(defn dump->csv
  [{{{lemma "value"} "de"} "lemmas" lex-cat       "lexicalCategory"
    id                     "id"     last-modified "modified" :as lexeme}]
  (when-let [pos (get lex-cat->pos lex-cat)]
    (when (and lemma (<= (count lemma) 256))
      (list [id lemma pos last-modified (pr-edn-str lexeme)]))))

(def gitup-access-token
  (getenv "GITUP_PAT"))

(def dwdsmor-packages-url
  "https://gitup.uni-potsdam.de/api/v4/projects/21585/packages")

(defn dwdsmor-index-url
  [version]
  (str
   "https://gitup.uni-potsdam.de/api/v4/projects/21585/packages/"
   "/generic/dwdsmor-dwds-index/" version "/dwdsmor-dwds-index.csv.xz"))

(defn dwdsmor-index-reader
  []
  (let [version (-> {:method       :get
                     :url          dwdsmor-packages-url
                     :headers      {"PRIVATE-TOKEN" gitup-access-token}
                     :query-params {"sort"         "desc"
                                    "package_name" "dwdsmor-dwds-index"}}
                    (hc/request) (deref) (get :body) (json/read-value)
                    (get-in [0 "version"]))]
    (->
     {:method  :get
      :url     (dwdsmor-index-url version)
      :headers {"PRIVATE-TOKEN" gitup-access-token}
      :as      :stream}
     (hc/request) (deref) (get :body) (XZCompressorInputStream.) (io/reader))))

(defn parse-dwdsmor-index
  [reader]
  (let [[header & records] (csv/read-csv reader)]
    (pmap (partial zipmap header) records)))

(defn dwdsmor-analysis->csv
  [{lemma       "analysis" pos            "pos"
    lemma-index "lidx"     paradigm-index "pidx"
    orth        "orthinfo" syn            "syninfo"
    cap?        "charinfo" meta           "metainfo"
    :as         form}]
  (when (and (pos-of-interest? pos)
             (valid-wikidata-lemma? lemma)
             (every? empty? [lemma-index paradigm-index orth syn cap? meta]))
    (list [(form "analysis")
           (form "pos")
           (form "spec")
           (form "inflected")
           (form "gender")
           (form "case")
           (form "person")
           (form "number")
           (form "nonfinite")
           (form "tense")
           (form "degree")
           (form "mood")
           (form "function")
           (form "auxiliary")
           (form "category")])))

(def copy-dwdsmor-analysis-sql
  "COPY dwdsmor_analysis
   (analysis, pos, spec, inflected, gender, casus, person, number, nonfinite,
    tense, degree, mood, funct, aux, category)
   FROM STDIN WITH (FORMAT CSV)")

(def copy-wikidata-lexeme-sql
  "COPY wikidata_lexeme (id, lemma, pos, last_modified, entity)
   FROM STDIN WITH (FORMAT CSV)")

(defn sync-progress-log
  [ctx records]
  (map-indexed
   #(do (when (zero? (mod %1 1000)) (tel/log! :info (format "%s: %,d" ctx %1))) %2)
   records))

(defn sync!
  [db]
  (pg/with-connection [c db]
    (pg/with-transaction [tx c]
      (pg/query tx "DELETE FROM dwdsmor_analysis")
      (pg/copy-in tx copy-dwdsmor-analysis-sql
                  (ring.io/piped-input-stream
                   (fn [os]
                     (with-open [r (dwdsmor-index-reader)
                                 w (io/writer os :encoding "UTF-8")]
                       (->> (parse-dwdsmor-index r)
                            (partition-all 32)
                            (mapcat #(pmap dwdsmor-analysis->csv %))
                            (mapcat identity)
                            (sync-progress-log "DWDSmor")
                            (csv/write-csv w))))))
      (pg/query tx "DELETE FROM wikidata_lexeme")
      (pg/copy-in tx copy-wikidata-lexeme-sql
                  (ring.io/piped-input-stream
                   (fn [os]
                     (with-open [r (dump-reader)
                                 w (io/writer os :encoding "UTF-8")]
                       (->> (parse-dump r)
                            (partition-all 32)
                            (mapcat #(pmap dump->csv %))
                            (mapcat identity)
                            (sync-progress-log "Wikidata")
                            (csv/write-csv w)))))))))

(defn read-wikidata-entity
  [m]
  (update m :entity (fnil read-string "{}")))

(def wikidata-columns
  [:id :lemma :modified :entity])

(def paradigm-columns
  (into wikidata-columns [:lidx :pidx]))

(defn read-paradigm
  [[{wd-id :id :as form} :as paradigm]]
  (cond-> {:dwdsmor (into []
                          (map #(reduce dissoc % paradigm-columns))
                          paradigm)}
    wd-id (assoc :wikidata
                 (read-wikidata-entity (select-keys form wikidata-columns)))))

(def wikidata-homograph-query-sql
  "SELECT lemma, pos
   FROM wikidata_lexeme
   GROUP BY lemma, pos
   HAVING COUNT(*) > 1")

(defn wikidata-homograph-pred
  [c]
  (let [homographs (pg/execute
                    c wikidata-homograph-query-sql
                    {:reduce [(fn [ss l] (conj ss [(l :lemma) (l :pos)]))
                              (sorted-set)]})]
    (fn [{:keys [lemma pos]}] (and lemma pos (homographs [lemma pos])))))

(defn query
  ([db xform f init]
   (query db "" xform f init))
  ([db where-clause xform f init]
   (pg/with-connection [c db]
     (pg/query
      c
      (str "SELECT
              wd.id as id,
              wd.lemma as lemma,
              wd.last_modified as modified,
              wd.entity as entity,
              da.*
            FROM dwdsmor_analysis da
            LEFT JOIN wikidata_lexeme wd
              ON da.analysis = wd.lemma and da.pos = wd.pos "
           where-clause
           " ORDER BY da.analysis, da.pos, da.spec")
      {:as (let [xform (comp (remove (wikidata-homograph-pred c))
                             (partition-by (juxt :analysis :pos))
                             (partition-all 32)
                             (mapcat (partial pmap read-paradigm))
                             xform)
                 f     (xform f)]
             (fn
               ([] init)
               ([acc] acc)
               ([acc row] (f acc row))))}))))

(def api-endpoint
  "https://www.wikidata.org/w/api.php")

(def api-login
  {:user     (getenv "WD_API_LOGIN_USER" "DwdsBot@DwdsBot")
   :password (getenv "WD_API_LOGIN_PASSWORD" "DwdsBot@DwdsBot")})

(def bot-signature
  "DwdsBot")

(defn edit-group-signature
  []
  (str/replace (Long/toHexString (. (java.util.Random.) (nextLong))) #"-" ""))

(defn edit-group-link
  []
  (format "[[:toolforge:editgroups/b/%s/%s|details]]"
          bot-signature (edit-group-signature)))

(defn edit-summary
  ([summary]
   (edit-summary summary (edit-group-link)))
  ([summary edit-group-link]
   (format "%s (%s)" summary edit-group-link)))

(defn ->multext
  [labels]
  (reduce-kv #(assoc %1 %2 {:language %2 :value %3}) {} labels))

(defn ->label
  [{{{en :value} :en {mul :value} :mul} :labels :as _entity}]
  (or mul en))

(def calendar-model
  "http://www.wikidata.org/entity/Q1985727")

(defn ->value
  [datatype v]
  (condp = datatype
    "string"          {:type  "string"
                       :value v}
    "monolingualtext" {:type  "monolingualtext"
                       :value {:text     (v :text)
                               :language (v :lang)}}
    "external-id"     {:type  "string"
                       :value v}
    "time"            {:type  "time"
                       :value {:calendarmodel calendar-model
                               :timezone      0
                               :time          (str "+" v "T00:00:00Z")
                               :precision     11
                               :after         0
                               :before        0}}
    "wikibase-item"   {:type  "wikibase-entityid"
                       :value {:entity-type "item"
                               :numeric-id  (parse-long (subs v 1))
                               :id          v}}))

(defn ->value-snak
  [{:keys [property datatype value]}]
  {:snaktype  "value"
   :property  property
   :datatype  datatype
   :datavalue (->value datatype value)})

(defn ->claim
  [{:keys [references] :as claim}]
  (cond-> {:type     "statement"
           :mainsnak (->value-snak claim)}
    references (assoc :references
                      {:snaks      (into [] (map ->value-snak) references)
                       :snak-order (into [] (map :property) references)})))

(defn ->sense
  [sense]
  (-> sense
      (update-existing :glosses ->multext)
      (update-existing :claims #(into [] (map ->claim) %))))

(defn ->form
  [form]
  (-> form
      (update-existing :representations ->multext)))

(defn ->data
  [entity]
  (->
   (dissoc entity :type)
   (update-existing :labels ->multext)
   (update-existing :descriptions ->multext)
   (update-existing :lemmas ->multext)
   (update-existing :claims #(into [] (map ->claim) %))
   (update-existing :senses #(into [] (map ->sense) %))
   (update-existing :forms #(into [] (map ->form) %))))

(defn wd-form?
  [{:keys [funct pos]}]
  (or (not= pos "ADJ") (= "Pred/Adv" funct)))

(defn wd-form-and-features
  [{:keys [tense number casus person degree mood nonfinite inflected funct]}]
  (cond-> [inflected]
    (= "Inf" nonfinite)  (conj (if (= "Cl" funct) "Q100952920" "Q179230"))
    (= "Pos" degree)     (conj "Q1931259" "Q3482678")
    (= "Comp" degree)    (conj "Q1931259" "Q14169499")
    (= "Sup" degree)     (conj "Q1931259" "Q1817208")
    (= "Ind" mood)       (conj "Q682111" "Q1317831")
    (= "Subj" mood)      (cond->
                             (= "Pres" tense) (conj "Q55685962" "Q1317831")
                             (= "Past" tense) (conj "Q54671845" "Q1317831"))
    (= "Imp" mood)       (conj "Q22716" "Q51929049" "Q1317831" "Q192613")
    (= "Part" nonfinite) (cond->
                             (= "Pres" tense) (conj "Q10345583")
                             (= "Perf" tense) (conj "Q12717679"))
    (= "Pres" tense)     (cond->
                             (not= "Part" nonfinite) (conj "Q192613"))
    (= "Past" tense)     (conj "Q442485")
    (= "Sg" number)      (conj "Q110786")
    (= "Pl" number)      (conj "Q146786")
    (= "Nom" casus)      (conj "Q131105")
    (= "Gen" casus)      (conj "Q146233")
    (= "Dat" casus)      (conj "Q145599")
    (= "Acc" casus)      (conj "Q146078")
    (= "1" person)       (conj "Q21714344")
    (= "2" person)       (conj "Q51929049")
    (= "3" person)       (conj "Q51929074")))

(defn form-sort-key
  [[_inflected & features]]
  (-> (map grammatical-feature-index features)
      (concat (repeat (- num-grammatical-features (count features)) 0))
      (vec)))

(defn form-data
  [[form & features]]
  {:representations     {:de form}
   :grammaticalFeatures (vec features)})

(defn lexeme->edit-data
  [{[{:keys [analysis pos]} :as forms] :dwdsmor {:keys [id entity]} :wikidata}]
  (let [forms (->> forms
                   (filter wd-form?)
                   (map wd-form-and-features)
                   (distinct)
                   (sort-by form-sort-key)
                   (map form-data)
                   (vec))]
    (cond
      ;; add forms
      (and entity (empty? (entity "forms")))
      (list {:id   id
             :data {:forms (into [] (map #(assoc % :add "")) forms)}})
      ;; create entity
      (nil? entity)
      (let [refs   [{:property "P248"
                     :datatype "wikibase-item"
                     :value    "Q108696977"}
                    {:property "P9940"
                     :datatype "external-id"
                     :value    analysis}
                    {:property "P813"
                     :datatype "time"
                     :value    (LocalDate/now)}]
            noun?  (= "NN" pos)
            genera (when noun? (into (sorted-set) (map :gender) forms))
            claims (concat
                    ;; DWDS external identifier
                    (list {:property   "P9940"
                           :datatype   "external-id"
                           :value      analysis
                           :references refs})
                    ;; pluraletantum
                    (when (and noun? (some? (genera "UnmGend")))
                      (list {:property   "P31"
                             :datatype   "wikibase-item"
                             :value      "Q138246"
                             :references refs}))
                    ;; genera
                    (for [g genera :let [g (gender->entity-id g)] :when g]
                      {:property   "P5185"
                       :datatype   "wikibase-item"
                       :value      g
                       :references refs}))]
        (list {:new  "lexeme"
               :data {:language        "Q188"
                      :lexicalCategory (pos->lex-cat pos)
                      :lemmas          {:de analysis}
                      :claims          claims
                      :forms           forms}})))))

(defn edit-data->request
  [summary csrf-token {:keys [data] :as edit-data}]
  (merge edit-data
         {:action  "wbeditentity"
          :bot     "true"
          :summary summary
          :data    (json/write-value-as-string (->data data))
          :token   csrf-token}))

(comment
  (query db/spec (comp (mapcat lexeme->edit-data) (random-sample 0.01)) conj [])
  (binding [jr/*url* api-endpoint] (jr/with-login api-login (jr/info))))
