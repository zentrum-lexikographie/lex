(ns zdl-lex-wikimedia.wiktionary.ld
  (:require [zdl-lex-wikimedia.dump :as dump]
            [zdl-lex-wikimedia.wiktionary.de :as de-wkt])
  (:import java.net.URI
           org.apache.jena.riot.RDFFormat
           [org.apache.jena.riot.system FactoryRDFStd PrefixMapStd StreamOps StreamRDFWriter]))

;; ## RDF-related helpers

(def rdf-factory
  "Apache Jena factory for RDF entities."
  (FactoryRDFStd.))

(defn quad
  "Creates quads."
  [g s p o]
  (.. rdf-factory (createQuad g s p o)))

(defn uri
  "Creates URIs."
  [uri]
  (.. rdf-factory (createURI uri)))

(defn bn
  "Creates blank nodes."
  []
  (.. rdf-factory (createBlankNode)))

(defn lt
  "Creates literals."
  ([v]
   (.. rdf-factory (createStringLiteral v)))
  ([v lang]
   (.. rdf-factory (createLangLiteral v lang))))

(defn de-wkt
  "Creates URIs in the `de.wiktionary.org` namespace."
  ([]
   (de-wkt "" nil))
  ([ln]
   (de-wkt ln nil))
  ([ln frag]
   (-> (URI. "https" "de.wiktionary.org" (str "/wiki/" ln) frag)
       (str) (uri))))

(def de-wkt-graph
  "URI of the`de.wiktionary.org` named-graph."
  (de-wkt))

(defn stmt
  "Creates statements, aka. quads in the Wiktionary graph."
  [s p o]
  (quad de-wkt-graph s p o))

(def prefixes
  "Namespace prefix mapping."
  (doto (PrefixMapStd.)
    (.add "rdf" "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
    (.add "ontolex" "http://www.w3.org/ns/lemon/ontolex#")
    (.add "skos" "http://www.w3.org/2004/02/skos#")
    (.add "isocat" "http://www.isocat.org/datcat/")
    (.add "dewkt" "https://de.wiktionary.org/ns#")))

(def qn
  "Resolves prefix/local-name tuples to qualified names (with caching)."
  (->> (fn [pre ln] (uri (.expand prefixes (name pre) (name ln))))
       (memoize)))

(defmacro with-ld-stream
  [os & body]
  `(let [ld-stream# (StreamRDFWriter/getWriterStream ~os RDFFormat/NQUADS)
         ~'->ld (fn [s# p# o#] (.quad ld-stream# (stmt s# p# o#)))]
    (.start ld-stream#)
    (StreamOps/sendPrefixesToStream prefixes ld-stream#)
    (let [result# ~@body]
      (.finish ld-stream#)
      result#)))

(defn type->ld
  "Converts parsed dictionary data to LD statements."
  [->ld {:keys [title pos-set pronounciation definitions] :as type}]
  (let [s (de-wkt title)
        prop #(do (->ld %1 (qn %2 %3) %4) %4)
        s-prop (partial prop s)
        lt-de #(lt % "de")]
    (->ld s (qn :rdf :type) (qn :ontolex :LexiconEntry))
    (doseq [pos (disj pos-set "Deutsch")]
      (->ld s (qn :dewkt :partOfSpeech) (lt-de pos)))
    (let [canon-form (s-prop :ontolex :canonicalForm (de-wkt title "form"))]
      (->ld canon-form (qn :ontolex :writtenRep) (lt-de title))
      (doseq [ipa pronounciation]
        (->ld canon-form (qn :ontolex :phoneticRep) (lt ipa "de-fonipa"))))
    (doseq [[idx {:keys [text]}] (map-indexed list definitions) :when (some? text)]
      (let [concept (s-prop :ontolex :evokes (de-wkt title (str "sense-" idx)))]
        (->ld concept (qn :rdf :type) (qn :ontolex :LexicalConcept))
        (->ld concept (qn :skos :definition) (lt-de text))))))

(defn -main [& args]
  (try
    (dump/with-revisions de-wkt/current-page-dump
      (with-ld-stream System/out
        (->> (de-wkt/parse-revisions revisions)
             (de-wkt/german-entries)
             (de-wkt/german-base-forms)
             (map (partial type->ld ->ld))
             (last))))
    (finally (shutdown-agents))))
