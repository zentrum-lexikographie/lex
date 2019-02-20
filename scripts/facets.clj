(require '[dwdsox.basex :as db])

(def metasearch-criteria
  [["Autor" "@Autor"]
   ["Quelle" "@Quelle"]
   ["Status" "@Status"]
   ["Tranche" "@Tranche"]
   ["Typ" "@Typ"]
   ["Wortfeld" "@Wortfeld" :disabled]])

(def systematik-xpath
  (partial str "descendant::*:Diasystematik[parent::*:Formangabe|parent::*:Lesart]"))

(def contentsearch-criteria
  [["Bedeutungsebene" (systematik-xpath "/*:Bedeutungsebene")]
   ["Fachgebiet" (systematik-xpath "/*:Fachgebiet")]
   ["Gebrauchszeitraum" (systematik-xpath "/*:Gebrauchszeitraum")]
   ["Gruppensprache" (systematik-xpath "/*:Gruppensprache")]
   ["Schreibung" "*:Formangabe/*:Schreibung" :disabled]
   ["Sprachraum" (systematik-xpath "/*:Sprachraum")]
   ["Stilebene" (systematik-xpath "/*:Stilebene")]
   ["Stilfärbung" (systematik-xpath "/*:Stilfaerbung")]
   ["Definition" "descendant::*:Definition" :disabled]
   ["Definition[Basis]" "descendant::*:Definition[@Typ='Basis']" :disabled]
   ["Definition[Meta]" "descendant::*:Definition[@Typ='Meta']" :disabled]
   ["Definition[General.]" "descendant::*:Definition[@Typ='Generalisierung']" :disabled]
   ["Definition[Spez.]" "descendant::*:Definition[@Typ='Spezifizierung']" :disabled]
   ["Definition[Enzykl.]" "descendant::*:Definition[@Typ='Enzyklopädie']" :disabled]
   ["morphologische Links" "self::*:Artikel/*:Verweise/*:Verweis/*:Ziellemma" :disabled]
   ["semantische Links" "descendant::*:Lesart/*:Verweise/*:Verweis/*:Ziellemma" :disabled]
   ["Unterlesarten" "descendant::*:Lesart//*:Lesart" :disabled]])

(defn- distinct-values [xpath]
  (into []
        (->>
         (->
          (db/simple-xml-query "" (str "distinct-values(//" xpath ")"))
          (.split "\\n+"))
         (map #(.trim  %))
         (sort-by #(.toLowerCase %)))))

(defn facets [criteria]
  (into
   (sorted-map)
   (pmap (fn
           [[title xpath disabled]]
           [title {:xpath xpath :values (if disabled [] (distinct-values xpath))}])
         criteria)))

(try
  (spit

   "resources/dwdsox/search-facets.edn"

   (pr-str
    {:meta (facets metasearch-criteria)
     :content (facets contentsearch-criteria)})

   :encoding "UTF-8")

  (finally (shutdown-agents)))
