(ns zdl-lex-wikimedia.lemon
  (:import java.net.URI
           [org.apache.jena.riot.system FactoryRDFStd PrefixMapStd]))

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
