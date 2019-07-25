(ns zdl-lex-wikimedia.jwktl
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [de.tudarmstadt.ukp.jwktl.api IWiktionaryEntry IWiktionaryPage IWiktionarySense PartOfSpeech WiktionaryFormatter]
           [de.tudarmstadt.ukp.jwktl.api.util ILanguage Language]
           de.tudarmstadt.ukp.jwktl.JWKTL
           java.io.Writer
           java.net.URI
           org.apache.jena.riot.RDFFormat
           [org.apache.jena.riot.system FactoryRDFStd PrefixMapStd StreamOps StreamRDFWriter]))

(def data-dir (io/file "data"))

(def wiktionary-de-dump (io/file data-dir "dewiktionary.xml"))
(def wiktionary-de-edition (io/file data-dir "dewiktionary"))

(def formatter (WiktionaryFormatter/instance))

(defmethod print-method IWiktionaryPage [^IWiktionaryPage p ^Writer w]
  (.write w (.formatPage formatter p (make-array ILanguage 0))))

(defmethod print-method IWiktionaryEntry [^IWiktionaryEntry e ^Writer w]
  (.write w (.formatEntry formatter e)))

(defmethod print-method IWiktionarySense [^IWiktionarySense s ^Writer w]
  (.write w (.formatSense formatter s)))

(defn create-edition []
  (when-not (.isDirectory wiktionary-de-edition)
    (JWKTL/parseWiktionaryDump wiktionary-de-dump wiktionary-de-edition false)))

(def rdf-factory (FactoryRDFStd.))

(def prefixes (doto (PrefixMapStd.)
                (.add "rdf" "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
                (.add "lemon" "http://lemon-model.net/lemon#")
                (.add "isocat" "http://www.isocat.org/datcat/")))


(defn rdf-quad [g s p o] (.. rdf-factory (createQuad g s p o)))

(defn rdf-uri [uri] (.. rdf-factory (createURI uri)))

(defn rdf-bn [] (.. rdf-factory (createBlankNode)))

(defn rdf-literal
  ([v] (.. rdf-factory (createStringLiteral v)))
  ([v lang] (.. rdf-factory (createLangLiteral v lang))))

(defn qn [pre ln]
  (-> (.expand prefixes pre ln) (rdf-uri)))

(defn wkt
  ([] (wkt "" nil))
  ([ln] (wkt ln nil))
  ([ln frag] (-> (URI. "https" "de.wiktionary.org" (str "/wiki/" ln) frag)
                 (str) (rdf-uri))))

(def wkt-graph (wkt))

(defn wkt-triple [s p o] (rdf-quad wkt-graph s p o))

(defn pos [^IWiktionaryEntry e]
  (some-> (.. e (getPartOfSpeech))
          {PartOfSpeech/NOUN "noun"
           PartOfSpeech/PROPER_NOUN "noun"
           PartOfSpeech/FIRST_NAME "name"
           PartOfSpeech/LAST_NAME "name"
           PartOfSpeech/TOPONYM "noun"
           PartOfSpeech/SINGULARE_TANTUM "noun"
           PartOfSpeech/PLURALE_TANTUM "noun"
           PartOfSpeech/MEASURE_WORD "noun"
           PartOfSpeech/VERB "verb"
           PartOfSpeech/AUXILIARY_VERB "verb"
           PartOfSpeech/ADJECTIVE "adjective"
           PartOfSpeech/ADVERB "adverb"
           PartOfSpeech/INTERJECTION "interjection"
           PartOfSpeech/SALUTATION nil
           PartOfSpeech/ONOMATOPOEIA nil
           PartOfSpeech/PHRASE nil
           PartOfSpeech/IDIOM nil
           PartOfSpeech/COLLOCATION nil
           PartOfSpeech/PROVERB nil
           PartOfSpeech/MNEMONIC nil
           PartOfSpeech/PRONOUN "pronoun"
           PartOfSpeech/PERSONAL_PRONOUN "pronoun"
           PartOfSpeech/REFLEXIVE_PRONOUN "pronoun"
           PartOfSpeech/POSSESSIVE_PRONOUN "pronoun"	
           PartOfSpeech/DEMONSTRATIVE_PRONOUN "pronoun"
           PartOfSpeech/RELATIVE_PRONOUN "pronoun"
           PartOfSpeech/INDEFINITE_PRONOUN "pronoun"
           PartOfSpeech/INTERROGATIVE_PRONOUN "pronoun"
           PartOfSpeech/INTERROGATIVE_ADVERB "pronoun"
           PartOfSpeech/PARTICLE "particle"
           PartOfSpeech/ANSWERING_PARTICLE "particle"
           PartOfSpeech/NEGATIVE_PARTICLE "particle"
           PartOfSpeech/COMPARATIVE_PARTICLE "particle"
           PartOfSpeech/FOCUS_PARTICLE "particle"
           PartOfSpeech/INTENSIFYING_PARTICLE "particle"
           PartOfSpeech/MODAL_PARTICLE "particle"
           PartOfSpeech/ARTICLE "article"
           PartOfSpeech/DETERMINER "determiner"
           PartOfSpeech/ABBREVIATION "abbreviation"
           PartOfSpeech/ACRONYM "acronym"
           PartOfSpeech/INITIALISM "initialism" 
           PartOfSpeech/CONTRACTION "contraction"
           PartOfSpeech/CONJUNCTION "conjunction"
           PartOfSpeech/SUBORDINATOR "subordinator"
           PartOfSpeech/PREPOSITION "preposition"
           PartOfSpeech/POSTPOSITION "postposition"
           PartOfSpeech/AFFIX "affix"
           PartOfSpeech/PREFIX "prefix"
           PartOfSpeech/SUFFIX "suffix"
           PartOfSpeech/PLACE_NAME_ENDING nil
           PartOfSpeech/LEXEME "lexeme"
           PartOfSpeech/CHARACTER nil
           PartOfSpeech/LETTER nil
           PartOfSpeech/NUMBER nil
           PartOfSpeech/NUMERAL nil
           PartOfSpeech/PUNCTUATION_MARK nil
           PartOfSpeech/SYMBOL "symbol"
           PartOfSpeech/HANZI nil
           PartOfSpeech/KANJI nil
           PartOfSpeech/KATAKANA nil
           PartOfSpeech/HIRAGANA nil
           PartOfSpeech/GISMU nil
           PartOfSpeech/WORD_FORM nil
           PartOfSpeech/PARTICIPLE nil
           PartOfSpeech/TRANSLITERATION nil
           PartOfSpeech/COMBINING_FORM nil
           PartOfSpeech/EXPRESSION nil
           PartOfSpeech/NOUN_PHRASE nil}))

(defn interesting-entry? [^IWiktionaryEntry e]
  (and (= Language/GERMAN (.. e (getWordLanguage))) (pos e)))

(comment
  (let [ref-counts (atom (sorted-map))]
    (with-open [edition (JWKTL/openEdition wiktionary-de-edition)
                entries (.getAllEntries edition)]
      (doseq [entry entries]
        (doseq [reference (.getReferences entry)]
          (when-let [ref (re-find #"Ref-([^\|\}]+)" (.getText reference))]
            (swap! ref-counts update
                   (-> ref second str/trim str/lower-case)
                   #(if % (inc %) 1)))))
      (csv/write-csv (io/file data-dir "ref.csv") (seq @ref-counts)))
    @ref-counts)

  (let [pos-counts (atom (sorted-map))]
    (with-open [edition (JWKTL/openEdition wiktionary-de-edition)
                entries (.getAllEntries edition)
                pos-csv (io/writer (io/file data-dir "pos.csv"))]
      (doseq [entry entries]
        (when-let [pos (.. entry (getPartOfSpeech))]
          (swap! pos-counts update
                 (-> (.. pos (name)) str/lower-case)
                 #(if % (inc %) 1))))
      (csv/write-csv pos-csv (seq @pos-counts)))
    @pos-counts)

  (time
   (with-open [rdf-stream (io/output-stream (io/file data-dir "wiktionary.trig"))
               edition (JWKTL/openEdition wiktionary-de-edition)
               entries (.getAllEntries edition)]
     (let [rdf-stream (StreamRDFWriter/getWriterStream rdf-stream RDFFormat/TRIG_BLOCKS)
           ->rdf (fn [s p o] (.quad rdf-stream (wkt-triple s p o)))
           rdf-type (qn "rdf" "type")
           lexicon-entry (qn "lemon" "LexiconEntry")
           part-of-speech (qn "isocat" "partOfSpeech")
           canonical-form (qn "lemon" "canonicalForm")
           written-rep (qn "lemon" "writtenRep")
           phonetic-form (qn "isocat" "phoneticForm")
           sense (qn "lemon" "sense")
           definition (qn "lemon" "definition")
           value (qn "lemon" "value")]
       (.start rdf-stream)
       (StreamOps/sendPrefixesToStream prefixes rdf-stream)
       (doseq [entry (take 100 (drop (rand-int 1000) entries))
               :when (interesting-entry? entry)
               :let [page (.getPage entry)
                     word (.getWord entry)
                     s (wkt word)
                     word-form (wkt word "form")]]
         (->rdf s rdf-type lexicon-entry)
         (->rdf s part-of-speech (rdf-literal (pos entry)))
         (->rdf s canonical-form word-form)
         (->rdf word-form written-rep (rdf-literal word "de"))
         (doseq [pron (or (.getPronunciations entry) [])
                 :when (= "IPA" (.. pron (getType) (name)))]
           (when-let [pron (not-empty (.. pron (getText)))]
             (->rdf word-form phonetic-form (rdf-literal pron))))
         (doseq [entry-sense (.. entry (getSenses true))
                 :when (.. entry-sense (getGloss))
                 :let [id (.. entry-sense (getId))
                       text (.. entry-sense (getGloss) (getPlainText))
                       sense-node (wkt word (str "sense-" id))
                       definition-node (wkt word (str "sense-def-" id))]]
           (->rdf s sense sense-node)
           (->rdf sense-node definition definition-node)
           (->rdf definition-node value (rdf-literal text))))
       (.finish rdf-stream)))))
