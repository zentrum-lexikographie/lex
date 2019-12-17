(ns zdl-lex-wikimedia.wiktionary.de
  (:refer-clojure :exclude [descendants])
  (:require [clojure.data.zip :refer [descendants right-locs]]
            [clojure.string :as str]
            [clojure.zip :as zip]
            [zdl-lex-wikimedia.wikitext :as wt]
            [zdl-lex-wikimedia.dump :as dump])
  (:import [org.sweble.wikitext.parser.nodes WtBody WtDefinitionList WtDefinitionListDef WtHeading WtInternalLink WtName WtSection WtTemplate WtTemplateArgument WtTemplateArguments WtValue]))

(def current-page-dump ["dewiktionary" "pages-meta-current" "latest" "xml" "bz2"])

(def page-history-dump ["dewiktionary" "pages-meta-history"])

(def ^:private namespace-filter
  "Administrative page namespaces"
  (complement
   #{
     "Benutzer"
     "Benutzer Diskussion"
     "Datei"
     "Datei Diskussion"
     "Diskussion"
     "Flexion"
     "Hilfe"
     "Hilfe Diskussion"
     "Kategorie"
     "Kategorie Diskussion"
     "MediaWiki"
     "MediaWiki Diskussion"
     "Medium"
     "Spezial"
     "Thesaurus"
     "Thesaurus Diskussion"
     "Verzeichnis"
     "Verzeichnis Diskussion"
     "Vorlage"
     "Vorlage Diskussion"
     "Wiktionary"
     "Wiktionary Diskussion"}))

(def ^:private regex-filter
  "Regex-based page filter"
  (complement
   (some-fn (partial re-seq #"^Archiv ")
            (partial re-seq #"^Liste ")
            (partial re-seq #" \(Konjugation\)$")
            (partial re-seq #" \(Deklination\)$"))))

(defn article? [{:keys [title]}]
  "Filters pages by title, removing administrative pages"
  (and title
       (let [[ns ln] (str/split title #":")]
         (or (nil? ln) (and (namespace-filter ns) (regex-filter title))))))

(defn ->clean-map [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))

(defn text [loc]
  "The trimmed text content of a WikiText node"
  (-> loc wt/loc->text str/trim not-empty))

(defn template-values [loc]
  "Extracts the argument values of a template node"
  (wt/nodes-> loc WtTemplateArguments WtTemplateArgument WtValue text))

(defn section-level [level]
  "A predicate matching a section (node) of a certain level"
  (fn [loc] (= level (.getLevel ^WtSection (zip/node loc)))))

(defn definitions [templates name]
  (->>
   (mapcat
    #(take 1 (wt/nodes-> % zip/up right-locs WtDefinitionList))
    (templates name))
   (mapcat
    #(wt/nodes-> % WtDefinitionListDef))
   (seq)))

(def reference-pattern #"^\[([^\]]+)\]\s*")

(defn remove-references [s]
  (not-empty (str/replace s reference-pattern "")))

(defn ->data [loc]
  (let [text (some-> loc text)]
    (-> {:idx (some->> text (re-seq reference-pattern) first second)
         :text (some-> text remove-references not-empty)
         :links (seq (wt/nodes-> loc descendants WtInternalLink zip/node
                                 #(.getTarget ^WtInternalLink %) wt/text))}
        (->clean-map)
        (not-empty))))

(defn parse-summary [templates]
  (when-let [summary (some->> templates
                              (filter (comp #(str/ends-with? % "Übersicht") first))
                              (first) (second)
                              (first))]
    (some->>
     (for [arg (wt/nodes-> summary WtTemplateArguments WtTemplateArgument)]
       (vector (wt/node-> arg WtName text) (wt/node-> arg WtValue text)))
     (seq)
     (into {}))))

(defn parse-types [loc]
  (let [pos (wt/nodes-> loc WtHeading WtTemplate [WtName "Wortart"] template-values)
        templates (->> (wt/nodes-> loc WtBody descendants WtTemplate)
                       (group-by #(or (wt/node-> % WtName text) "")))
        definitions (partial definitions templates)
        ->data (comp seq (partial map ->data) definitions)
        join (comp seq concat)]
    (->clean-map
     {:pos-set (apply sorted-set pos)
      :summary (parse-summary templates)
      :pronounciation (some->>
                       (definitions "Aussprache")
                       (filter #(wt/node-> % WtTemplate WtName "IPA"))
                       (mapcat #(wt/nodes-> % WtTemplate [WtName "Lautschrift"] text))
                       (seq))
      :hyphenation (->data "Worttrennung")
      :definitions (->data "Bedeutungen")
      :synonyms (->data "Synonyme")
      :examples (->data "Beispiele")
      :collocations (join (->data "Charakteristische Wortkombinationen")
                          (->data "Signifikante Kollokation"))
      :hyperonyms (->data "Oberbegriffe")
      :hyponyms (->data "Unterbegriffe")
      :antonyms (join (->data "Gegenworte")
                      (->data "Gegenwörter"))
      :etymology (->data "Herkunft")
      :etym-related (->data "Sinnverwandte Wörter")
      :derived (join (->data "Wortbildungen")
                     (->data "Abgeleitete Begriffe"))
      :references (some->>
                   (definitions "Referenzen")
                   (mapcat #(wt/nodes-> % WtTemplate WtName text))
                   (seq)
                   (apply sorted-set))
      :translations (->> (join (templates "Üt") (templates "Ü"))
                         (map template-values)
                         (filter (comp (partial < 1) count))
                         (seq))})))

(defn parse-entry [title loc]
  (let [heading (wt/node-> loc WtHeading)]
    {:title title
     :heading (text heading)
     :lang (wt/node-> heading WtTemplate [WtName "Sprache"] template-values)
     :types (wt/nodes-> loc WtBody WtSection (section-level 3) parse-types)}))

(defn parse-article [{:keys [title text] :as revision}]
  (if-let [loc (some-> text wt/parse wt/zipper)]
    (->> (wt/nodes-> loc WtSection (section-level 2) (partial parse-entry title))
         (assoc revision :entries))
    revision))

(defn parse-revisions
  [revisions]
  (->> revisions (filter article?) (pmap parse-article)))

(defn entries
  ([articles]
   (entries (constantly true) entries))
  ([pred articles]
   (for [{:keys [entries] :as article} articles
         :let [article (dissoc article :entries)]
         entry entries
         :when (pred entry)]
     (merge article entry))))

(defn german? [{:keys [lang]}]
  (= "Deutsch" lang))

(def german-entries
  (partial entries german?))

(defn types
  ([entries]
   (types (constantly true) entries))
  ([pred entries]
   (for [{:keys [types] :as entry} entries
         :let [entry (dissoc entry :types)]
         type types
         :when (pred type)]
     (merge entry type))))

(def base-form-pos
  #{#_"Abkürzung" #_"Abkürzung (Deutsch)" "Adjektiv" "Adverb" "Affix"
  "Antwortpartikel" "Artikel" #_"Buchstabe" #_"Deklinierte Form"
  #_"Dekliniertes Gerundivum" "Demonstrativpronomen" "Eigenname" #_"Englisch"
  #_"Enklitikon" #_"Erweiterter Infinitiv" "Fokuspartikel" #_"Formel"
  #_"Gebundenes Lexem" #_"Geflügeltes Wort" "Gradpartikel" #_"Grußformel" #_"Hilfsverb"
  "Indefinitpronomen" "Interjektion" #_"International" "Interrogativadverb"
  "Interrogativpronomen" #_"Klitikon" #_"Komparativ" #_"Konjugierte Form"
  "Konjunktion" "Konjunktionaladverb" "Kontraktion" "Lokaladverb" #_"Merkspruch"
  "Modaladverb" "Modalpartikel" "Nachname" "Negationspartikel" "Numerale"
  "Onomatopoetikum" #_"Ortsnamengrundwort" "Partikel" #_"Partizip I" #_"Partizip II"
  "Personalpronomen" "Possessivpronomen" #_"Postposition" "Pronomen"
  "Pronominaladverb" #_"Präfix" #_"Präfixoid" "Präposition" "Pseudopartizip"
  #_"Redewendung" "Reflexivpronomen" "Relativpronomen" "Reziprokpronomen"
  #_"Sprichwort" #_"Straßenname" #_"Subjunktion" "Substantiv" #_"Suffix" #_"Suffixoid"
  #_"Superlativ" "Temporaladverb" "Toponym" "Verb" "Vergleichspartikel" "Vorname"
  #_"Wiederholungszahlwort" #_"Wortverbindung" #_"Zahlklassifikator" #_"Zahlzeichen"
  #_"Zirkumposition" #_"gebundenes Lexem"})

(defn base-form? [{:keys [pos-set]}]
  (some base-form-pos (or pos-set #{})))

(def german-base-forms
  (partial types base-form?))
