(require '[clojure.pprint :refer [pprint]]
         '[dwdsox.basex :as db])

(defn query [xpath]
  "Queries distinct node values by a given XPath"
  (let [query (str "distinct-values(//" xpath ")")
        values (-> (db/simple-xml-query "" query) (.split "\\n+"))
        sorted (->> values (map #(.trim  %)) (sort-by #(.toLowerCase %)))]
    (into [] sorted)))

(defn with-values [[title xpath type explicit-values]]
  "Ammends a criterion specification with values for this criterion"
  {:type type :title title :xpath xpath :values (or explicit-values (query xpath))})

(defn criteria [specs]
  "Maps criteria specifications to its values"
  (into [] (pmap with-values specs)))

(def systematik-xpath
  (partial str "descendant::*:Diasystematik[parent::*:Formangabe|parent::*:Lesart]"))

(def definition-xpath
  (partial str "descendant::*:Definition"))

(try
  (let [facets
        (criteria
         [["Autor" "@Autor" :meta]
          ["Quelle" "@Quelle" :meta]
          ["Status" "@Status" :meta]
          ["Tranche" "@Tranche" :meta]
          ["Typ" "@Typ" :meta]
          ["Wortfeld" "@Wortfeld" :meta []]
          ["Schreibung" "*:Formangabe/*:Schreibung" [] :content]
          ["Unterlesarten" "descendant::*:Lesart//*:Lesart" :content ["*"]]
          ["Definition" (definition-xpath) :content []]
          ["Definition[Basis]" (definition-xpath "[@Typ='Basis']") :content []]
          ["Definition[Meta]" (definition-xpath "[@Typ='Meta']") :content []]
          ["Definition[General.]" (definition-xpath "[@Typ='Generalisierung']") :content []]
          ["Definition[Spez.]" (definition-xpath "[@Typ='Spezifizierung']") :content []]
          ["Definition[Enzykl.]" (definition-xpath "[@Typ='Enzyklopädie']") :content []]
          ["Bedeutungsebene" (systematik-xpath "/*:Bedeutungsebene") :content]
          ["Fachgebiet" (systematik-xpath "/*:Fachgebiet") :content]
          ["Gebrauchszeitraum" (systematik-xpath "/*:Gebrauchszeitraum") :content]
          ["Gruppensprache" (systematik-xpath "/*:Gruppensprache") :content]
          ["Sprachraum" (systematik-xpath "/*:Sprachraum") :content]
          ["Stilebene" (systematik-xpath "/*:Stilebene") :content]
          ["Stilfärbung" (systematik-xpath "/*:Stilfaerbung") :content]
          ["morphologische Links" "self::*:Artikel/*:Verweise/*:Verweis/*:Ziellemma" :content []]
          ["semantische Links" "descendant::*:Lesart/*:Verweise/*:Verweis/*:Ziellemma" :content []]])]
    (spit
     "resources/dwdsox/search-facets.edn"
     (with-out-str (pprint facets))
     :encoding "UTF-8"))

  (finally (shutdown-agents)))
