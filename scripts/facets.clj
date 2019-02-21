(require '[clojure.pprint :refer [pprint]]
         '[dwdsox.basex :as db])

(defn query [xpath]
  "Queries distinct node values by a given XPath"
  (let [query (str "distinct-values(//" xpath ")")
        values (-> (db/simple-xml-query "" query) (.split "\\n+"))
        sorted (->> values (map #(.trim  %)) (sort-by #(.toLowerCase %)))]
    (into [] sorted)))

(defn with-values [[title xpath explicit-values]]
  "Ammends a criterion specification with values for this criterion"
  [title {:xpath xpath :values (or explicit-values (query xpath))}])

(defn criteria [specs]
  "Maps criteria specifications to its values"
  (into (sorted-map) (pmap with-values specs)))

(def systematik-xpath
  (partial str "descendant::*:Diasystematik[parent::*:Formangabe|parent::*:Lesart]"))

(def definition-xpath
  (partial str "descendant::*:Definition"))

(try
  (let [facets
        {:meta (criteria
                [["Autor" "@Autor"]
                 ["Quelle" "@Quelle"]
                 ["Status" "@Status"]
                 ["Tranche" "@Tranche"]
                 ["Typ" "@Typ"]
                 ["Wortfeld" "@Wortfeld" []]])

         :content (criteria
                   [["Bedeutungsebene" (systematik-xpath "/*:Bedeutungsebene")]
                    ["Fachgebiet" (systematik-xpath "/*:Fachgebiet")]
                    ["Gebrauchszeitraum" (systematik-xpath "/*:Gebrauchszeitraum")]
                    ["Gruppensprache" (systematik-xpath "/*:Gruppensprache")]
                    ["Schreibung" "*:Formangabe/*:Schreibung" []]
                    ["Sprachraum" (systematik-xpath "/*:Sprachraum")]
                    ["Stilebene" (systematik-xpath "/*:Stilebene")]
                    ["Stilfärbung" (systematik-xpath "/*:Stilfaerbung")]
                    ["Definition" (definition-xpath) []]
                    ["Definition[Basis]" (definition-xpath "[@Typ='Basis']") []]
                    ["Definition[Meta]" (definition-xpath "[@Typ='Meta']") []]
                    ["Definition[General.]" (definition-xpath "[@Typ='Generalisierung']") []]
                    ["Definition[Spez.]" (definition-xpath "[@Typ='Spezifizierung']") []]
                    ["Definition[Enzykl.]" (definition-xpath "[@Typ='Enzyklopädie']") []]
                    ["morphologische Links" "self::*:Artikel/*:Verweise/*:Verweis/*:Ziellemma" []]
                    ["semantische Links" "descendant::*:Lesart/*:Verweise/*:Verweis/*:Ziellemma" []]
                    ["Unterlesarten" "descendant::*:Lesart//*:Lesart" ["*"]]])}]
    (spit
     "resources/dwdsox/search-facets.edn"
     (with-out-str (pprint facets))
     :encoding "UTF-8"))

  (finally (shutdown-agents)))
